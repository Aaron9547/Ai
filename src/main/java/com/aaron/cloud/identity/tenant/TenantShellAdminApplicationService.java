package com.aaron.cloud.identity.tenant;

import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService.PutItem;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final TenantOutboundResilienceRuntime tenantOutboundResilienceRuntime;
    private final TenantBrandLogoApplicationService tenantBrandLogoApplicationService;
    private final AiOutboundResilienceProperties baselineOutboundProps;
    private final ObjectMapper objectMapper;

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
        return new ShellConfigResponse(
                branding, modelCalling, new OutboundSectionDto(tenantJson, baselineJson, effectiveMerged));
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
        String limits = jsonOrDefault(body.getChatPromptLimitsJson(), "{}");
        String memPol = jsonOrDefault(body.getMemoryPolicyJson(), "{}");
        String guard = jsonOrDefault(body.getChatInputGuardJson(), "{}");
        String rounds =
                body.getWebSearchGroundingMultiRoundCount() == null
                                || body.getWebSearchGroundingMultiRoundCount().isBlank()
                        ? "3"
                        : body.getWebSearchGroundingMultiRoundCount().trim();
        String suffixes = jsonArrayOrDefault(body.getWebSearchGroundingRoundSuffixesJson(), "[]");
        if (limits.length() > RUNTIME_JSON_MAX_CHARS
                || memPol.length() > RUNTIME_JSON_MAX_CHARS
                || guard.length() > RUNTIME_JSON_MAX_CHARS
                || suffixes.length() > RUNTIME_JSON_MAX_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 字段过长");
        }
        List<PutItem> items = new ArrayList<>();
        items.add(item(TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID, mem));
        items.add(item(TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON, limits));
        items.add(item(TenantRuntimeSettingKey.MEMORY_POLICY_JSON, memPol));
        items.add(item(TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON, guard));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT, rounds));
        items.add(item(TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON, suffixes));
        tenantRuntimeSettingApplicationService.replace(tenantId, items);
        return load(tenantId);
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

    private ModelCallingRuntimeDto readModelCallingRuntime(long tenantId) {
        return new ModelCallingRuntimeDto(
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.MEMORY_EMBEDDING_VECTOR_MODEL_ID),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.MEMORY_POLICY_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.CHAT_INPUT_GUARD_JSON),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT),
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON));
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
            BrandingDto branding, ModelCallingRuntimeDto modelCallingRuntime, OutboundSectionDto outbound) {}

    public record ModelCallingRuntimeDto(
            String memoryEmbeddingVectorModelId,
            String chatPromptLimitsJson,
            String memoryPolicyJson,
            String chatInputGuardJson,
            String webSearchGroundingMultiRoundCount,
            String webSearchGroundingRoundSuffixesJson) {}

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

    @Data
    public static class ShellModelCallingPutBody {
        private String memoryEmbeddingVectorModelId;
        private String chatPromptLimitsJson;
        private String memoryPolicyJson;
        private String chatInputGuardJson;
        /** 1～10 的十进制字符串，与 {@link TenantRuntimeSettingKey#WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT} 一致 */
        private String webSearchGroundingMultiRoundCount;
        private String webSearchGroundingRoundSuffixesJson;
    }
}
