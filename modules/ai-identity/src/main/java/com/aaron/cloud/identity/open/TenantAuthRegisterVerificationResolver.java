package com.aaron.cloud.identity.open;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 按租户解析注册验证码发送配置（{@code AUTH_REGISTER_VERIFICATION_JSON} + 平台参数默认）。 */
@Component
@RequiredArgsConstructor
public class TenantAuthRegisterVerificationResolver {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final PlatformSettingApplicationService platformSettings;
    private final ObjectMapper objectMapper;

    public AuthRegisterVerificationConfig resolve(long tenantId) {
        String raw =
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.AUTH_REGISTER_VERIFICATION_JSON);
        AuthRegisterVerificationConfig cfg = parseOrDefault(raw);
        if (cfg.getCodeTtlSeconds() <= 0) {
            cfg.setCodeTtlSeconds(platformSettings.getInt(PlatformSettingKey.AUTH_EMAIL_CODE_TTL_SECONDS));
        }
        if (cfg.getSendCooldownSeconds() <= 0) {
            cfg.setSendCooldownSeconds(
                    platformSettings.getInt(PlatformSettingKey.AUTH_EMAIL_SEND_COOLDOWN_SECONDS));
        }
        if (cfg.getCodeLength() < 4 || cfg.getCodeLength() > 8) {
            cfg.setCodeLength(6);
        }
        return cfg;
    }

    public String toJson(AuthRegisterVerificationConfig cfg) {
        try {
            return objectMapper.writeValueAsString(cfg == null ? new AuthRegisterVerificationConfig() : cfg);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid auth register verification config", e);
        }
    }

    private AuthRegisterVerificationConfig parseOrDefault(String raw) {
        if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
            return new AuthRegisterVerificationConfig();
        }
        try {
            AuthRegisterVerificationConfig parsed =
                    objectMapper.readValue(raw.trim(), AuthRegisterVerificationConfig.class);
            return parsed == null ? new AuthRegisterVerificationConfig() : parsed;
        } catch (Exception e) {
            return new AuthRegisterVerificationConfig();
        }
    }
}
