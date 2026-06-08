package com.aaron.cloud.identity.tenant;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService.PutItem;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingMessages;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetScheduledTaskSynchronizer;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import com.aaron.cloud.common.message.MessageSceneReadinessQuery;
import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig;
import com.aaron.cloud.identity.open.TenantAuthRegisterVerificationResolver;
import com.aaron.cloud.common.api.ports.SiteCrawlRuntimePort;
import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;
import com.aaron.cloud.common.rag.crawl.SiteCrawlRuntimeValidator;
import com.aaron.cloud.common.api.ports.TenantRagRuntimePort;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TenantShellAdminApplicationService {

    private static final int MAX_LOGO_URL = 2048;
    private static final int MAX_PORTAL_TITLE = 255;
    private static final int MAX_FOOTER = 2000;
    private static final int RUNTIME_JSON_MAX_CHARS = 65_000;

    private final SysTenantRepository sysTenantRepository;
    private final SysLlmModelRepository llmModelRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final TenantOutboundResilienceRuntime tenantOutboundResilienceRuntime;
    private final TenantRuntimeSettingMessages runtimeMessages;
    private final TenantBrandLogoApplicationService tenantBrandLogoApplicationService;
    private final AiOutboundResilienceProperties baselineOutboundProps;
    private final ObjectMapper objectMapper;
    private final SiteCrawlRuntimePort siteCrawlRuntimePort;
    private final TenantRagRuntimePort tenantRagRuntimePort;
    private final TenantAuthRegisterVerificationResolver authRegisterVerificationResolver;
    private final KnowledgePlanetTenantRuntime knowledgePlanetTenantRuntime;
    private final MessageSceneReadinessQuery messageSceneReadinessQuery;
    private final KnowledgePlanetScheduledTaskSynchronizer knowledgePlanetScheduledTaskSynchronizer;
    private final TenantBrandingResolver tenantBrandingResolver;

    public ShellConfigResponse load(long tenantId) {
        sysTenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        TenantBrandingResolver.TenantBrandingSnapshot snap = tenantBrandingResolver.resolve(tenantId);
        BrandingDto branding =
                new BrandingDto(
                        snap.logoUrl(), snap.portalTitle(), snap.footerText(), snap.portalTitleResolved());
        String tenantJson =
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.OUTBOUND_RESILIENCE_JSON);
        JsonNode baselineJson = objectMapper.valueToTree(baselineOutboundProps);
        JsonNode effectiveMerged =
                objectMapper.valueToTree(tenantOutboundResilienceRuntime.effective(tenantId));
        ModelCallingRuntimeDto modelCalling = readModelCallingRuntime(tenantId);
        AuthRegisterRuntimeDto authRegister = readAuthRegisterRuntime(tenantId);
        KnowledgePlanetRuntimeDto knowledgePlanet = readKnowledgePlanetRuntime(tenantId);
        return new ShellConfigResponse(
                branding,
                modelCalling,
                authRegister,
                knowledgePlanet,
                new OutboundSectionDto(tenantJson, baselineJson, effectiveMerged));
    }

    /** 兼容旧客户端：一次写入外观与出站。 */
    public ShellConfigResponse save(long tenantId, ShellPutBody body) {
        saveBranding(tenantId, toBrandingBody(body));
        saveOutbound(tenantId, new ShellOutboundPutBody(body.getOutboundResilienceJson()));
        return load(tenantId);
    }

    /** 仅更新管理端外观（LOGO / 标题 / 页脚）；空串按 null 落库，表示使用默认展示。 */
    public ShellConfigResponse saveBranding(long tenantId, ShellBrandingPutBody body) {
        String logo = normalizeOptional(body.getLogoUrl());
        String title = normalizeOptional(body.getPortalTitle());
        String footer = normalizeOptional(body.getFooterText());
        validateBrandingLengths(logo, title, footer);
        sysTenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        sysTenantRepository.updateAdminBranding(tenantId, logo, title, footer);
        return load(tenantId);
    }

    /** 仅更新租户出站韧性 JSON 覆盖层。 */
    public ShellConfigResponse saveOutbound(long tenantId, ShellOutboundPutBody body) {
        String outboundRaw = body.outboundResilienceJson();
        if (outboundRaw == null || outboundRaw.isBlank()) {
            outboundRaw = "{}";
        } else {
            outboundRaw = outboundRaw.trim();
        }
        if (outboundRaw.length() > RUNTIME_JSON_MAX_CHARS) {
            throw runtimeMessages.badRequest(TenantRuntimeSettingMessages.Shell.OUTBOUND_JSON_TOO_LONG);
        }
        PutItem item = new PutItem();
        item.setKey(TenantRuntimeSettingKey.OUTBOUND_RESILIENCE_JSON.getStorage());
        item.setValueText(outboundRaw);
        tenantRuntimeSettingApplicationService.replace(tenantId, List.of(item));
        return load(tenantId);
    }

    /** 记忆嵌入、对话拼装预算、记忆策略、输入护栏、联网多轮与后缀；写入 {@code ten_runtime_setting}。 */
    public ShellConfigResponse saveModelCallingRuntime(long tenantId, ShellModelCallingPutBody body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String mem = body.getMemoryEmbeddingVectorModelId() == null ? "" : body.getMemoryEmbeddingVectorModelId().trim();
        String webModelId =
                body.getWebSearchGroundingModelId() == null ? "" : body.getWebSearchGroundingModelId().trim();
        String fixedSourcesJson =
                jsonArrayOrDefault(body.getWebSearchGroundingFixedSourcesJson(), "[]");
        validateWebSearchGroundingSelection(tenantId, webModelId, fixedSourcesJson);
        String rewriteModelId =
                body.getWebSearchQueryRewriteModelId() == null
                        ? ""
                        : body.getWebSearchQueryRewriteModelId().trim();
        validateOptionalRewriteModelId(tenantId, rewriteModelId);
        String limits = jsonOrDefault(body.getChatPromptLimitsJson(), "{}");
        String memPol = jsonOrDefault(body.getMemoryPolicyJson(), "{}");
        String guard = jsonOrDefault(body.getChatInputGuardJson(), "{}");
        String rounds =
                body.getWebSearchGroundingMultiRoundCount() == null
                                || body.getWebSearchGroundingMultiRoundCount().isBlank()
                        ? "3"
                        : body.getWebSearchGroundingMultiRoundCount().trim();
        String suffixes = jsonArrayOrDefault(body.getWebSearchGroundingRoundSuffixesJson(), "[]");
        String cacheJson = jsonOrDefault(body.getWebSearchGroundingCacheJson(), "{}");
        String fixedOutboundJson = jsonOrDefault(body.getWebSearchFixedSourceOutboundJson(), "{}");
        String crawlPreset =
                body.getSiteCrawlPreset() == null || body.getSiteCrawlPreset().isBlank()
                        ? SiteCrawlPreset.BALANCED.name()
                        : SiteCrawlRuntimeValidator.normalizePresetStorage(body.getSiteCrawlPreset());
        String crawlRuntimeJson = jsonOrDefault(body.getSiteCrawlRuntimeJson(), "{}");
        SiteCrawlRuntimeValidator.parseObject(crawlRuntimeJson, objectMapper);
        String ragDimPersist =
                tenantRagRuntimePort.normalizeVectorDimensionForPersist(
                        tenantId, body.getRagVectorDimension());
        String ragRetrieval =
                tenantRagRuntimePort.normalizeRetrievalModeForPersist(body.getRagRetrievalMode());
        if (limits.length() > RUNTIME_JSON_MAX_CHARS
                || memPol.length() > RUNTIME_JSON_MAX_CHARS
                || guard.length() > RUNTIME_JSON_MAX_CHARS
                || suffixes.length() > RUNTIME_JSON_MAX_CHARS
                || cacheJson.length() > RUNTIME_JSON_MAX_CHARS
                || fixedOutboundJson.length() > RUNTIME_JSON_MAX_CHARS
                || crawlRuntimeJson.length() > RUNTIME_JSON_MAX_CHARS) {
            throw runtimeMessages.badRequest(TenantRuntimeSettingMessages.Shell.JSON_FIELDS_TOO_LONG);
        }
        List<PutItem> items = new ArrayList<>();
        items.add(item(TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID, mem));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID, webModelId));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON, fixedSourcesJson));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_QUERY_REWRITE_MODEL_ID, rewriteModelId));
        items.addAll(clearLegacyWebSearchQueryRewriteModelKeys());
        items.add(item(TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON, limits));
        items.add(item(TenantRuntimeSettingKey.MEMORY_POLICY_JSON, memPol));
        items.add(item(TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON, guard));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT, rounds));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON, suffixes));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_CACHE_JSON, cacheJson));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON, fixedOutboundJson));
        items.add(item(TenantRuntimeSettingKey.SITE_CRAWL_PRESET, crawlPreset));
        items.add(item(TenantRuntimeSettingKey.SITE_CRAWL_RUNTIME_JSON, crawlRuntimeJson));
        if (ragDimPersist != null) {
            items.add(item(TenantRuntimeSettingKey.RAG_VECTOR_DIMENSION, ragDimPersist));
        }
        items.add(item(TenantRuntimeSettingKey.RAG_RETRIEVAL_MODE, ragRetrieval));
        String ragTuning = jsonOrDefault(body.getRagRetrievalTuningJson(), "{}");
        if (ragTuning.length() > RUNTIME_JSON_MAX_CHARS) {
            throw runtimeMessages.badRequest(TenantRuntimeSettingMessages.Shell.JSON_FIELDS_TOO_LONG);
        }
        items.add(item(TenantRuntimeSettingKey.RAG_RETRIEVAL_TUNING_JSON, ragTuning));
        tenantRuntimeSettingApplicationService.replace(tenantId, items);
        return load(tenantId);
    }

    /** 开放注册开关 + 验证码参数（邮件通道见消息中心）。 */
    public ShellConfigResponse saveAuthRegister(long tenantId, ShellAuthRegisterPutBody body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        boolean emailReady =
                messageSceneReadinessQuery.isSceneConfigured(
                        tenantId, MessageSceneCode.REGISTER_VERIFICATION);
        if (body.isOpenRegistration() && !emailReady) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开启自助注册须先在「消息发送管理」配置 REGISTER_VERIFICATION 场景模板与 SMTP 通道");
        }
        boolean persistOpen = body.isOpenRegistration() && emailReady;

        AuthRegisterVerificationConfig cfg = new AuthRegisterVerificationConfig();
        cfg.setCodeLength(body.getCodeLength() <= 0 ? 6 : body.getCodeLength());
        cfg.setCodeTtlSeconds(body.getCodeTtlSeconds() <= 0 ? 600 : body.getCodeTtlSeconds());
        cfg.setSendCooldownSeconds(body.getSendCooldownSeconds() <= 0 ? 60 : body.getSendCooldownSeconds());

        List<PutItem> items = new ArrayList<>();
        PutItem openReg = new PutItem();
        openReg.setKey(TenantRuntimeSettingKey.AUTH_OPEN_REGISTRATION.getStorage());
        openReg.setValueText(persistOpen ? "true" : "false");
        items.add(openReg);

        String json = authRegisterVerificationResolver.toJson(cfg);
        if (json.length() > RUNTIME_JSON_MAX_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码配置 JSON 过长");
        }
        items.add(item(TenantRuntimeSettingKey.AUTH_REGISTER_VERIFICATION_JSON, json));
        tenantRuntimeSettingApplicationService.replace(tenantId, items);
        return load(tenantId);
    }

    /** 个人知识星球：开关、可选沉淀模型；周报邮件见消息中心 KNOWLEDGE_PLANET_WEEKLY 模板。 */
    public ShellConfigResponse saveKnowledgePlanet(long tenantId, ShellKnowledgePlanetPutBody body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        validateOptionalChatModelId(
                tenantId, body.getDigestModelId(), TenantRuntimeSettingKey.KNOWLEDGE_PLANET_DIGEST_MODEL_ID);
        if (body.isEnabled()
                && body.isEmailEnabled()
                && !messageSceneReadinessQuery.isSceneConfigured(
                        tenantId, MessageSceneCode.KNOWLEDGE_PLANET_WEEKLY)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开启周报邮件须先在「消息发送管理」配置 KNOWLEDGE_PLANET_WEEKLY 场景模板");
        }

        List<PutItem> items = new ArrayList<>();
        items.add(item(TenantRuntimeSettingKey.KNOWLEDGE_PLANET_ENABLED, body.isEnabled() ? "true" : "false"));
        items.add(
                item(
                        TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED,
                        body.isEmailEnabled() ? "true" : "false"));
        String digestId = trimOrEmpty(body.getDigestModelId());
        items.add(item(TenantRuntimeSettingKey.KNOWLEDGE_PLANET_DIGEST_MODEL_ID, digestId));
        items.add(
                item(
                        TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_BOOK_SEARCH_ENABLED,
                        body.isWeeklyBookSearchEnabled() ? "true" : "false"));
        items.add(
                item(
                        TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_MIN_NODES,
                        String.valueOf(Math.max(0, body.getWeeklyMinNodes()))));
        tenantRuntimeSettingApplicationService.replace(tenantId, items);
        knowledgePlanetScheduledTaskSynchronizer.syncForTenant(tenantId);
        return load(tenantId);
    }

    private KnowledgePlanetRuntimeDto readKnowledgePlanetRuntime(long tenantId) {
        boolean enabled = knowledgePlanetTenantRuntime.isEnabled(tenantId);
        boolean emailEnabled = knowledgePlanetTenantRuntime.isWeeklyEmailEnabled(tenantId);
        boolean emailReady =
                emailEnabled
                        && messageSceneReadinessQuery.isSceneConfigured(
                                tenantId, MessageSceneCode.KNOWLEDGE_PLANET_WEEKLY);
        return new KnowledgePlanetRuntimeDto(
                enabled,
                knowledgePlanetTenantRuntime.digestModelId(tenantId).map(String::valueOf).orElse(""),
                emailEnabled,
                emailReady,
                knowledgePlanetTenantRuntime.isWeeklyBookSearchEnabled(tenantId),
                knowledgePlanetTenantRuntime.weeklyMinNodes(tenantId));
    }

    private void validateOptionalChatModelId(long tenantId, String rawId, TenantRuntimeSettingKey fieldKey) {
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_ID_INVALID);
        }
        SysLlmModel model =
                llmModelRepository
                        .findById(tenantId, id)
                        .orElseThrow(
                                () ->
                                        runtimeMessages.badField(
                                                fieldKey,
                                                TenantRuntimeSettingMessages.Validation.MODEL_NOT_FOUND_IN_TENANT));
        if (model.getModelKind() != LlmModelKind.LANGUAGE) {
            throw runtimeMessages.badField(
                    fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_LANGUAGE_CHAT);
        }
        if (model.getStatus() != LlmModelStatus.ACTIVE) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_ACTIVE);
        }
    }

    private AuthRegisterRuntimeDto readAuthRegisterRuntime(long tenantId) {
        boolean storedOpen =
                tenantRuntimeSettingApplicationService.isAuthOpenRegistrationEnabled(tenantId);
        AuthRegisterVerificationConfig cfg = authRegisterVerificationResolver.resolve(tenantId);
        boolean emailReady =
                messageSceneReadinessQuery.isSceneConfigured(
                        tenantId, MessageSceneCode.REGISTER_VERIFICATION);
        return new AuthRegisterRuntimeDto(
                storedOpen && emailReady,
                emailReady,
                cfg.getCodeLength(),
                cfg.getCodeTtlSeconds(),
                cfg.getSendCooldownSeconds());
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static PutItem item(TenantRuntimeSettingKey key, String valueText) {
        PutItem i = new PutItem();
        i.setKey(key.getStorage());
        i.setValueText(valueText);
        return i;
    }

    private static String jsonOrDefault(String raw, String emptyDefault) {
        if (raw == null || raw.isBlank()) {
            return emptyDefault;
        }
        return raw.trim();
    }

    private static String jsonArrayOrDefault(String raw, String emptyDefault) {
        if (raw == null || raw.isBlank()) {
            return emptyDefault;
        }
        return raw.trim();
    }

    private void validateWebSearchGroundingSelection(
            long tenantId, String arkModelId, String fixedSourcesJson) {
        boolean hasArk = arkModelId != null && !arkModelId.isBlank();
        if (hasArk) {
            validateOptionalWebSearchModelId(tenantId, arkModelId);
        }
        boolean hasFixed = hasNonEmptyFixedSourcesJson(fixedSourcesJson);
        if (!hasArk && !hasFixed) {
            throw runtimeMessages.badRequest(TenantRuntimeSettingMessages.Shell.WEB_SEARCH_GROUNDING_REQUIRED);
        }
    }

    private boolean hasNonEmptyFixedSourcesJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json.trim());
            return node.isArray() && !node.isEmpty();
        } catch (Exception e) {
            throw runtimeMessages.badField(
                    TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON,
                    TenantRuntimeSettingMessages.Validation.INVALID_JSON);
        }
    }

    private String effectiveQueryRewriteModelId(long tenantId) {
        return tenantRuntimeSettingApplicationService
                .webSearchQueryRewriteModelId(tenantId)
                .map(String::valueOf)
                .orElse("");
    }

    @SuppressWarnings("deprecation")
    private static List<PutItem> clearLegacyWebSearchQueryRewriteModelKeys() {
        return List.of(
                item(TenantRuntimeSettingKey.WEB_SEARCH_QUERY_REWRITE_LANGUAGE_MODEL_ID, ""),
                item(TenantRuntimeSettingKey.WEB_SEARCH_QUERY_REWRITE_WEB_SEARCH_MODEL_ID, ""));
    }

    private void validateOptionalRewriteModelId(long tenantId, String rawId) {
        TenantRuntimeSettingKey fieldKey = TenantRuntimeSettingKey.WEB_SEARCH_QUERY_REWRITE_MODEL_ID;
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_ID_INVALID);
        }
        SysLlmModel model =
                llmModelRepository
                        .findById(tenantId, id)
                        .orElseThrow(
                                () ->
                                        runtimeMessages.badField(
                                                fieldKey,
                                                TenantRuntimeSettingMessages.Validation.MODEL_NOT_FOUND_IN_TENANT));
        if (model.getStatus() != LlmModelStatus.ACTIVE) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_ACTIVE);
        }
        LlmModelKind kind = model.getModelKind();
        if (kind == LlmModelKind.LANGUAGE) {
            return;
        }
        if (kind == LlmModelKind.WEB_SEARCH) {
            return;
        }
        throw runtimeMessages.badField(
                fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_LANGUAGE_OR_WEB_SEARCH);
    }

    private void validateOptionalWebSearchModelId(long tenantId, String rawId) {
        TenantRuntimeSettingKey fieldKey = TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID;
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_ID_OR_EMPTY);
        }
        SysLlmModel m =
                llmModelRepository
                        .findById(tenantId, id)
                        .orElseThrow(
                                () ->
                                        runtimeMessages.badField(
                                                fieldKey,
                                                TenantRuntimeSettingMessages.Validation.MODEL_NOT_FOUND_IN_TENANT));
        if (m.getModelKind() != LlmModelKind.WEB_SEARCH) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_WEB_SEARCH);
        }
        if (m.getStatus() != LlmModelStatus.ACTIVE) {
            throw runtimeMessages.badField(fieldKey, TenantRuntimeSettingMessages.Validation.MODEL_MUST_BE_ACTIVE);
        }
    }

    private ModelCallingRuntimeDto readModelCallingRuntime(long tenantId) {
        String ragDimStored = tenantRagRuntimePort.storedVectorDimensionRaw(tenantId);
        int ragDimEffective = tenantRagRuntimePort.resolveVectorDimension(tenantId);
        String ragRetStored = tenantRagRuntimePort.storedRetrievalModeRaw(tenantId);
        String ragRetEffective = tenantRagRuntimePort.resolveRetrievalModeStorage(tenantId);
        return new ModelCallingRuntimeDto(
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON),
                effectiveQueryRewriteModelId(tenantId),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.MEMORY_POLICY_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_CACHE_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.SITE_CRAWL_PRESET),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.SITE_CRAWL_RUNTIME_JSON),
                ragDimStored,
                ragDimEffective,
                tenantRagRuntimePort.isVectorDimensionLocked(tenantId),
                ragRetStored,
                ragRetEffective,
                tenantRagRuntimePort.processDefaultVectorDimension(),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.RAG_RETRIEVAL_TUNING_JSON));
    }

    private static ShellBrandingPutBody toBrandingBody(ShellPutBody body) {
        ShellBrandingPutBody b = new ShellBrandingPutBody();
        b.setLogoUrl(body.getLogoUrl());
        b.setPortalTitle(body.getPortalTitle());
        b.setFooterText(body.getFooterText());
        return b;
    }

    private static void validateBrandingLengths(String logo, String title, String footer) {
        if (logo != null && logo.length() > MAX_LOGO_URL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "logoUrl 过长");
        }
        if (title != null && title.length() > MAX_PORTAL_TITLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portalTitle 过长");
        }
        if (footer != null && footer.length() > MAX_FOOTER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "footerText 过长");
        }
    }

    public LogoUploadResponse uploadBrandLogo(MultipartFile file) throws Exception {
        long tid = TenantContextHolder.require().getTenantId();
        SysTenant t =
                sysTenantRepository
                        .findById(tid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        String code = t.getCode();
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户缺少 code，无法存储 LOGO 文件");
        }
        String url = tenantBrandLogoApplicationService.store(file, code.trim());
        return new LogoUploadResponse(url);
    }

    private static String normalizeOptional(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public record ShellConfigResponse(
            BrandingDto branding,
            ModelCallingRuntimeDto modelCallingRuntime,
            AuthRegisterRuntimeDto authRegister,
            KnowledgePlanetRuntimeDto knowledgePlanet,
            OutboundSectionDto outbound) {}

    public record KnowledgePlanetRuntimeDto(
            boolean enabled,
            String digestModelId,
            boolean emailEnabled,
            boolean emailDeliveryReady,
            boolean weeklyBookSearchEnabled,
            int weeklyMinNodes) {}

    public record AuthRegisterRuntimeDto(
            boolean openRegistration,
            boolean emailDeliveryReady,
            int codeLength,
            int codeTtlSeconds,
            int sendCooldownSeconds) {}

    public record ModelCallingRuntimeDto(
            String memoryEmbeddingVectorModelId,
            String webSearchGroundingModelId,
            String webSearchGroundingFixedSourcesJson,
            String webSearchQueryRewriteModelId,
            String chatPromptLimitsJson,
            String memoryPolicyJson,
            String chatInputGuardJson,
            String webSearchGroundingMultiRoundCount,
            String webSearchGroundingRoundSuffixesJson,
            String webSearchGroundingCacheJson,
            String webSearchFixedSourceOutboundJson,
            String siteCrawlPreset,
            String siteCrawlRuntimeJson,
            String ragVectorDimension,
            int ragVectorDimensionEffective,
            boolean ragVectorDimensionLocked,
            String ragRetrievalMode,
            String ragRetrievalModeEffective,
            int processDefaultVectorDimension,
            String ragRetrievalTuningJson) {}

    public record BrandingDto(
            String logoUrl, String portalTitle, String footerText, String portalTitleResolved) {}

    public record OutboundSectionDto(String tenantJson, JsonNode baselineJson, JsonNode effectiveMerged) {}

    public record LogoUploadResponse(String url) {}

    @Data
    public static class ShellPutBody {
        private String logoUrl;
        private String portalTitle;
        private String footerText;
        private String outboundResilienceJson;
    }

    @Data
    public static class ShellBrandingPutBody {
        private String logoUrl;
        private String portalTitle;
        private String footerText;
    }

    public record ShellOutboundPutBody(String outboundResilienceJson) {}

    /** 非 CUSTOM 档位对应的默认 {@code SITE_CRAWL_RUNTIME_JSON} 模板（供管理端切换档位时填充）。 */
    public Map<String, String> siteCrawlRuntimeTemplate(String presetCode) {
        SiteCrawlPreset preset;
        try {
            preset = SiteCrawlPreset.valueOf(presetCode == null ? "" : presetCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid site crawl preset");
        }
        if (preset == SiteCrawlPreset.CUSTOM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOM has no template");
        }
        return Map.of("json", siteCrawlRuntimePort.defaultRuntimeJsonForPreset(preset));
    }

    @Data
    public static class ShellModelCallingPutBody {
        private String memoryEmbeddingVectorModelId;
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MODEL_ID}；火山 Ark，可留空 */
        private String webSearchGroundingModelId;
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON}；内置固定源代码数组 */
        private String webSearchGroundingFixedSourcesJson;
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_QUERY_REWRITE_MODEL_ID} */
        private String webSearchQueryRewriteModelId;
        private String chatPromptLimitsJson;
        private String memoryPolicyJson;
        private String chatInputGuardJson;
        /** 1～10 的十进制字符串，与 {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT} 一致 */
        private String webSearchGroundingMultiRoundCount;
        private String webSearchGroundingRoundSuffixesJson;
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_CACHE_JSON}；{@code {}} 表示服务端内置默认 */
        private String webSearchGroundingCacheJson;
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON} */
        private String webSearchFixedSourceOutboundJson;
        /** {@link TenantRuntimeSettingKey#SITE_CRAWL_PRESET} */
        private String siteCrawlPreset;
        /** {@link TenantRuntimeSettingKey#SITE_CRAWL_RUNTIME_JSON} */
        private String siteCrawlRuntimeJson;
        /** {@link TenantRuntimeSettingKey#RAG_VECTOR_DIMENSION}；空表示尚未锁定、走进程默认 */
        private String ragVectorDimension;
        /** {@link TenantRuntimeSettingKey#RAG_RETRIEVAL_MODE}；空表示走 {@code ai.rag.retrieval-mode} */
        private String ragRetrievalMode;
        /** {@link TenantRuntimeSettingKey#RAG_RETRIEVAL_TUNING_JSON} */
        private String ragRetrievalTuningJson;
    }

    @Data
    public static class ShellKnowledgePlanetPutBody {
        private boolean enabled;
        private String digestModelId;
        /** 是否期望发送周报邮件（须消息中心 KNOWLEDGE_PLANET_WEEKLY 模板就绪） */
        private boolean emailEnabled = true;
        private boolean weeklyBookSearchEnabled = true;
        private int weeklyMinNodes = 2;
    }

    @Data
    public static class ShellAuthRegisterPutBody {
        private boolean openRegistration;
        private int codeLength;
        private int codeTtlSeconds;
        private int sendCooldownSeconds;
    }
}
