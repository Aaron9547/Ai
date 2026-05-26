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
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetScheduledTaskSynchronizer;
import com.aaron.cloud.common.knowledgeplanet.KnowledgePlanetTenantRuntime;
import com.aaron.cloud.identity.knowledgeplanet.KnowledgePlanetEmailConfig;
import com.aaron.cloud.identity.knowledgeplanet.TenantKnowledgePlanetEmailResolver;
import com.aaron.cloud.identity.open.AuthRegisterEmailSupport;
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
    private final TenantBrandLogoApplicationService tenantBrandLogoApplicationService;
    private final AiOutboundResilienceProperties baselineOutboundProps;
    private final ObjectMapper objectMapper;
    private final SiteCrawlRuntimePort siteCrawlRuntimePort;
    private final TenantRagRuntimePort tenantRagRuntimePort;
    private final TenantAuthRegisterVerificationResolver authRegisterVerificationResolver;
    private final KnowledgePlanetTenantRuntime knowledgePlanetTenantRuntime;
    private final TenantKnowledgePlanetEmailResolver knowledgePlanetEmailResolver;
    private final KnowledgePlanetScheduledTaskSynchronizer knowledgePlanetScheduledTaskSynchronizer;

    public ShellConfigResponse load(long tenantId) {
        SysTenant t =
                sysTenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        String baseName = t.getName() == null ? "" : t.getName().trim();
        String rawLogo = t.getAdminLogoUrl() == null ? "" : t.getAdminLogoUrl().trim();
        String rawTitle = t.getAdminPortalTitle() == null ? "" : t.getAdminPortalTitle().trim();
        String rawFooter = t.getAdminFooterText() == null ? "" : t.getAdminFooterText().trim();
        String portalResolved = rawTitle.isEmpty() ? baseName : rawTitle;
        BrandingDto branding = new BrandingDto(rawLogo, rawTitle, rawFooter, portalResolved);
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "出站 JSON 过长");
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
        validateOptionalWebSearchModelId(tenantId, webModelId);
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
                || crawlRuntimeJson.length() > RUNTIME_JSON_MAX_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 字段过长");
        }
        List<PutItem> items = new ArrayList<>();
        items.add(item(TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID, mem));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MODEL_ID, webModelId));
        items.add(item(TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON, limits));
        items.add(item(TenantRuntimeSettingKey.MEMORY_POLICY_JSON, memPol));
        items.add(item(TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON, guard));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT, rounds));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON, suffixes));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_CACHE_JSON, cacheJson));
        items.add(item(TenantRuntimeSettingKey.SITE_CRAWL_PRESET, crawlPreset));
        items.add(item(TenantRuntimeSettingKey.SITE_CRAWL_RUNTIME_JSON, crawlRuntimeJson));
        if (ragDimPersist != null) {
            items.add(item(TenantRuntimeSettingKey.RAG_VECTOR_DIMENSION, ragDimPersist));
        }
        items.add(item(TenantRuntimeSettingKey.RAG_RETRIEVAL_MODE, ragRetrieval));
        tenantRuntimeSettingApplicationService.replace(tenantId, items);
        return load(tenantId);
    }

    /** 开放注册开关 + 注册验证码邮件（SMTP 与模板）。 */
    public ShellConfigResponse saveAuthRegister(long tenantId, ShellAuthRegisterPutBody body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        AuthRegisterVerificationConfig.EmailChannel em = new AuthRegisterVerificationConfig.EmailChannel();
        em.setSmtpHost(trimOrEmpty(body.getEmailSmtpHost()));
        em.setSmtpPort(body.getEmailSmtpPort() <= 0 ? 465 : body.getEmailSmtpPort());
        em.setUsername(trimOrEmpty(body.getEmailUsername()));
        em.setPassword(resolveEmailPassword(tenantId, body.getEmailPassword()));
        em.setFrom(trimOrEmpty(body.getEmailFrom()));
        em.setSsl(body.isEmailSsl());
        em.setSubjectTemplate(trimOrEmpty(body.getEmailSubjectTemplate()));
        em.setBodyTemplate(body.getEmailBodyTemplate() == null ? "" : body.getEmailBodyTemplate());

        boolean emailReady = AuthRegisterEmailSupport.isDeliveryReady(em);
        if (body.isOpenRegistration() && !emailReady) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开启自助注册须先配置 SMTP 主机、发件人地址、SMTP 用户名与 SMTP 密码（QQ 邮箱须填授权码）");
        }
        boolean persistOpen = body.isOpenRegistration() && emailReady;

        AuthRegisterVerificationConfig cfg = new AuthRegisterVerificationConfig();
        cfg.setCodeLength(body.getCodeLength() <= 0 ? 6 : body.getCodeLength());
        cfg.setCodeTtlSeconds(body.getCodeTtlSeconds() <= 0 ? 600 : body.getCodeTtlSeconds());
        cfg.setSendCooldownSeconds(body.getSendCooldownSeconds() <= 0 ? 60 : body.getSendCooldownSeconds());
        cfg.setEmail(em);

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

    /** 个人知识星球：开关、Cron、邮件模板、沉淀模型；并同步 {@code ten_scheduled_task}。 */
    public ShellConfigResponse saveKnowledgePlanet(long tenantId, ShellKnowledgePlanetPutBody body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        validateOptionalChatModelId(tenantId, body.getDigestModelId(), "知识星球模型");

        KnowledgePlanetEmailConfig emailCfg = knowledgePlanetEmailResolver.resolve(tenantId);
        emailCfg.setEnabled(body.isEmailEnabled());
        emailCfg.setReuseRegisterSmtp(body.isReuseRegisterSmtp());
        var em = emailCfg.getEmail();
        em.setSmtpHost(trimOrEmpty(body.getEmailSmtpHost()));
        em.setSmtpPort(body.getEmailSmtpPort() <= 0 ? 465 : body.getEmailSmtpPort());
        em.setUsername(trimOrEmpty(body.getEmailUsername()));
        em.setPassword(resolveKnowledgePlanetEmailPassword(tenantId, body.getEmailPassword()));
        em.setFrom(trimOrEmpty(body.getEmailFrom()));
        em.setSsl(body.isEmailSsl());
        em.setSubjectTemplate(trimOrEmpty(body.getEmailSubjectTemplate()));
        em.setBodyTemplate(body.getEmailBodyTemplate() == null ? "" : body.getEmailBodyTemplate());

        boolean emailReady = AuthRegisterEmailSupport.isDeliveryReady(em);
        if (body.isEnabled() && body.isEmailEnabled() && !emailReady) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "开启周报邮件须配置 SMTP（或复用注册邮箱且注册 SMTP 已就绪）");
        }

        List<PutItem> items = new ArrayList<>();
        items.add(item(TenantRuntimeSettingKey.KNOWLEDGE_PLANET_ENABLED, body.isEnabled() ? "true" : "false"));
        items.add(
                item(
                        TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_COMPUTE_CRON,
                        trimOrEmpty(body.getWeeklyComputeCron()).isEmpty()
                                ? KnowledgePlanetTenantRuntime.DEFAULT_COMPUTE_CRON
                                : body.getWeeklyComputeCron().trim()));
        items.add(
                item(
                        TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_EMAIL_CRON,
                        trimOrEmpty(body.getWeeklyEmailCron()).isEmpty()
                                ? KnowledgePlanetTenantRuntime.DEFAULT_EMAIL_CRON
                                : body.getWeeklyEmailCron().trim()));
        String digestId = trimOrEmpty(body.getDigestModelId());
        items.add(item(TenantRuntimeSettingKey.KNOWLEDGE_PLANET_DIGEST_MODEL_ID, digestId));
        String emailJson = knowledgePlanetEmailResolver.toJson(emailCfg);
        if (emailJson.length() > RUNTIME_JSON_MAX_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识星球邮件 JSON 过长");
        }
        items.add(item(TenantRuntimeSettingKey.KNOWLEDGE_PLANET_EMAIL_JSON, emailJson));
        tenantRuntimeSettingApplicationService.replace(tenantId, items);
        knowledgePlanetScheduledTaskSynchronizer.syncForTenant(tenantId);
        return load(tenantId);
    }

    private KnowledgePlanetRuntimeDto readKnowledgePlanetRuntime(long tenantId) {
        boolean enabled = knowledgePlanetTenantRuntime.isEnabled(tenantId);
        KnowledgePlanetEmailConfig cfg = knowledgePlanetEmailResolver.resolve(tenantId);
        var em = cfg.getEmail();
        return new KnowledgePlanetRuntimeDto(
                enabled,
                knowledgePlanetTenantRuntime.weeklyComputeCron(tenantId),
                knowledgePlanetTenantRuntime.weeklyEmailCron(tenantId),
                knowledgePlanetTenantRuntime.digestModelId(tenantId).map(String::valueOf).orElse(""),
                cfg.isEnabled(),
                cfg.isReuseRegisterSmtp(),
                knowledgePlanetEmailResolver.isDeliveryReady(tenantId),
                em.getSmtpHost(),
                em.getSmtpPort(),
                em.getUsername(),
                isPasswordConfigured(em.getPassword()),
                em.getFrom(),
                em.isSsl(),
                em.getSubjectTemplate(),
                em.getBodyTemplate());
    }

    private String resolveKnowledgePlanetEmailPassword(long tenantId, String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            String t = incoming.trim();
            if (isAdminPasswordMaskSentinel(t)) {
                return readKnowledgePlanetStoredPassword(tenantId);
            }
            return t;
        }
        return readKnowledgePlanetStoredPassword(tenantId);
    }

    private String readKnowledgePlanetStoredPassword(long tenantId) {
        KnowledgePlanetEmailConfig existing = knowledgePlanetEmailResolver.resolve(tenantId);
        String stored = existing.getEmail().getPassword();
        return stored == null ? "" : stored;
    }

    private void validateOptionalChatModelId(long tenantId, String rawId, String label) {
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " id 无效");
        }
        SysLlmModel model =
                llmModelRepository
                        .findById(tenantId, id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 不存在"));
        if (model.getModelKind() != LlmModelKind.LANGUAGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 须为 LANGUAGE 对话模型");
        }
        if (model.getStatus() != LlmModelStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 未启用");
        }
    }

    private AuthRegisterRuntimeDto readAuthRegisterRuntime(long tenantId) {
        boolean storedOpen =
                tenantRuntimeSettingApplicationService.isAuthOpenRegistrationEnabled(tenantId);
        AuthRegisterVerificationConfig cfg = authRegisterVerificationResolver.resolve(tenantId);
        AuthRegisterVerificationConfig.EmailChannel em = cfg.getEmail();
        boolean emailReady = AuthRegisterEmailSupport.isDeliveryReady(em);
        return new AuthRegisterRuntimeDto(
                storedOpen && emailReady,
                emailReady,
                cfg.getCodeLength(),
                cfg.getCodeTtlSeconds(),
                cfg.getSendCooldownSeconds(),
                em.getSmtpHost(),
                em.getSmtpPort(),
                em.getUsername(),
                isPasswordConfigured(em.getPassword()),
                em.getFrom(),
                em.isSsl(),
                em.getSubjectTemplate(),
                em.getBodyTemplate());
    }

    private String resolveEmailPassword(long tenantId, String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            String t = incoming.trim();
            if (isAdminPasswordMaskSentinel(t)) {
                return readStoredEmailPassword(tenantId);
            }
            return t;
        }
        return readStoredEmailPassword(tenantId);
    }

    private String readStoredEmailPassword(long tenantId) {
        AuthRegisterVerificationConfig existing = authRegisterVerificationResolver.resolve(tenantId);
        String stored = existing.getEmail().getPassword();
        return stored == null ? "" : stored;
    }

    /** 管理端「已配置」占位符误提交时保留库内原密码。 */
    private static boolean isAdminPasswordMaskSentinel(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String t = value.trim();
        if ("********".equals(t) || "••••••••".equals(t)) {
            return true;
        }
        return t.chars().allMatch(ch -> ch == '*' || ch == '•');
    }

    private static boolean isPasswordConfigured(String password) {
        return password != null && !password.isBlank();
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

    private void validateOptionalWebSearchModelId(long tenantId, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "WEB_SEARCH_GROUNDING_MODEL_ID 须为数字主键或留空");
        }
        SysLlmModel m =
                llmModelRepository
                        .findById(tenantId, id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "联网检索模型不存在或不属于本租户：" + id));
        if (m.getModelKind() != LlmModelKind.WEB_SEARCH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "所选模型须为「联网搜索」(WEB_SEARCH) 类型");
        }
        if (m.getStatus() != LlmModelStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选联网搜索模型须为启用状态");
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
                        tenantId, TenantRuntimeSettingKey.SITE_CRAWL_PRESET),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.SITE_CRAWL_RUNTIME_JSON),
                ragDimStored,
                ragDimEffective,
                tenantRagRuntimePort.isVectorDimensionLocked(tenantId),
                ragRetStored,
                ragRetEffective,
                tenantRagRuntimePort.processDefaultVectorDimension());
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
            String weeklyComputeCron,
            String weeklyEmailCron,
            String digestModelId,
            boolean emailEnabled,
            boolean reuseRegisterSmtp,
            boolean emailDeliveryReady,
            String emailSmtpHost,
            int emailSmtpPort,
            String emailUsername,
            boolean emailPasswordConfigured,
            String emailFrom,
            boolean emailSsl,
            String emailSubjectTemplate,
            String emailBodyTemplate) {}

    public record AuthRegisterRuntimeDto(
            boolean openRegistration,
            boolean emailDeliveryReady,
            int codeLength,
            int codeTtlSeconds,
            int sendCooldownSeconds,
            String emailSmtpHost,
            int emailSmtpPort,
            String emailUsername,
            boolean emailPasswordConfigured,
            String emailFrom,
            boolean emailSsl,
            String emailSubjectTemplate,
            String emailBodyTemplate) {}

    public record ModelCallingRuntimeDto(
            String memoryEmbeddingVectorModelId,
            String webSearchGroundingModelId,
            String chatPromptLimitsJson,
            String memoryPolicyJson,
            String chatInputGuardJson,
            String webSearchGroundingMultiRoundCount,
            String webSearchGroundingRoundSuffixesJson,
            String webSearchGroundingCacheJson,
            String siteCrawlPreset,
            String siteCrawlRuntimeJson,
            String ragVectorDimension,
            int ragVectorDimensionEffective,
            boolean ragVectorDimensionLocked,
            String ragRetrievalMode,
            String ragRetrievalModeEffective,
            int processDefaultVectorDimension) {}

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
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MODEL_ID}；留空则按 sort_order 默认 */
        private String webSearchGroundingModelId;
        private String chatPromptLimitsJson;
        private String memoryPolicyJson;
        private String chatInputGuardJson;
        /** 1～10 的十进制字符串，与 {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT} 一致 */
        private String webSearchGroundingMultiRoundCount;
        private String webSearchGroundingRoundSuffixesJson;
        /** {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_CACHE_JSON}；{@code {}} 表示服务端内置默认 */
        private String webSearchGroundingCacheJson;
        /** {@link TenantRuntimeSettingKey#SITE_CRAWL_PRESET} */
        private String siteCrawlPreset;
        /** {@link TenantRuntimeSettingKey#SITE_CRAWL_RUNTIME_JSON} */
        private String siteCrawlRuntimeJson;
        /** {@link TenantRuntimeSettingKey#RAG_VECTOR_DIMENSION}；空表示尚未锁定、走进程默认 */
        private String ragVectorDimension;
        /** {@link TenantRuntimeSettingKey#RAG_RETRIEVAL_MODE}；空表示走 {@code ai.rag.retrieval-mode} */
        private String ragRetrievalMode;
    }

    @Data
    public static class ShellKnowledgePlanetPutBody {
        private boolean enabled;
        private String weeklyComputeCron;
        private String weeklyEmailCron;
        private String digestModelId;
        private boolean emailEnabled = true;
        private boolean reuseRegisterSmtp = true;
        private String emailSmtpHost;
        private int emailSmtpPort;
        private String emailUsername;
        private String emailPassword;
        private String emailFrom;
        private boolean emailSsl = true;
        private String emailSubjectTemplate;
        private String emailBodyTemplate;
    }

    @Data
    public static class ShellAuthRegisterPutBody {
        private boolean openRegistration;
        private int codeLength;
        private int codeTtlSeconds;
        private int sendCooldownSeconds;
        private String emailSmtpHost;
        private int emailSmtpPort;
        private String emailUsername;
        private String emailPassword;
        private String emailFrom;
        private boolean emailSsl;
        private String emailSubjectTemplate;
        private String emailBodyTemplate;
    }
}
