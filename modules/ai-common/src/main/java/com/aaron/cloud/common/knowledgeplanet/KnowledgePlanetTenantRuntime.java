package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgePlanetTenantRuntime {

    public static final String DEFAULT_COMPUTE_CRON = "0 0 3 * * MON";
    public static final String DEFAULT_EMAIL_CRON = "0 0 9 * * MON";

    private final TenantRuntimeSettingApplicationService runtimeSettings;

    public boolean isEnabled(long tenantId) {
        String raw = runtimeSettings.getEffectiveValueText(tenantId, TenantRuntimeSettingKey.KNOWLEDGE_PLANET_ENABLED);
        return Boolean.parseBoolean(raw == null ? "false" : raw.trim());
    }

    public boolean isWeeklyEmailEnabled(long tenantId) {
        String raw =
                runtimeSettings.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED);
        return Boolean.parseBoolean(raw == null ? "true" : raw.trim());
    }

    public String weeklyComputeCron(long tenantId) {
        return cronOrDefault(tenantId, TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_COMPUTE_CRON, DEFAULT_COMPUTE_CRON);
    }

    public String weeklyEmailCron(long tenantId) {
        return cronOrDefault(tenantId, TenantRuntimeSettingKey.KNOWLEDGE_PLANET_WEEKLY_EMAIL_CRON, DEFAULT_EMAIL_CRON);
    }

    public Optional<Long> digestModelId(long tenantId) {
        String raw = runtimeSettings.getEffectiveValueText(tenantId, TenantRuntimeSettingKey.KNOWLEDGE_PLANET_DIGEST_MODEL_ID);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String cronOrDefault(long tenantId, TenantRuntimeSettingKey key, String def) {
        String raw = runtimeSettings.getEffectiveValueText(tenantId, key);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        return raw.trim();
    }
}
