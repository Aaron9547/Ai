package com.aaron.cloud.identity.knowledgeplanet;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.identity.open.AuthRegisterEmailSupport;
import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig;
import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig.EmailChannel;
import com.aaron.cloud.identity.open.TenantAuthRegisterVerificationResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantKnowledgePlanetEmailResolver {

    private final TenantRuntimeSettingApplicationService runtimeSettings;
    private final TenantAuthRegisterVerificationResolver registerResolver;
    private final ObjectMapper objectMapper;

    public KnowledgePlanetEmailConfig resolve(long tenantId) {
        String raw =
                runtimeSettings.getEffectiveValueText(tenantId, TenantRuntimeSettingKey.KNOWLEDGE_PLANET_EMAIL_JSON);
        KnowledgePlanetEmailConfig cfg;
        try {
            if (raw == null || raw.isBlank() || "{}".equals(raw.trim())) {
                cfg = new KnowledgePlanetEmailConfig();
            } else {
                cfg = objectMapper.readValue(raw.trim(), KnowledgePlanetEmailConfig.class);
            }
        } catch (Exception e) {
            cfg = new KnowledgePlanetEmailConfig();
        }
        if (cfg.getEmail() == null) {
            cfg.setEmail(new EmailChannel());
        }
        if (cfg.isReuseRegisterSmtp()) {
            AuthRegisterVerificationConfig reg = registerResolver.resolve(tenantId);
            EmailChannel regEm = reg.getEmail();
            EmailChannel em = cfg.getEmail();
            em.setSmtpHost(regEm.getSmtpHost());
            em.setSmtpPort(regEm.getSmtpPort());
            em.setUsername(regEm.getUsername());
            if (em.getPassword() == null || em.getPassword().isBlank()) {
                em.setPassword(regEm.getPassword());
            }
            if (em.getFrom() == null || em.getFrom().isBlank()) {
                em.setFrom(regEm.getFrom());
            }
            em.setSsl(regEm.isSsl());
        }
        return cfg;
    }

    public boolean isDeliveryReady(long tenantId) {
        KnowledgePlanetEmailConfig cfg = resolve(tenantId);
        if (!cfg.isEnabled()) {
            return false;
        }
        return AuthRegisterEmailSupport.isDeliveryReady(cfg.getEmail());
    }

    public String toJson(KnowledgePlanetEmailConfig cfg) {
        try {
            return objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            return "{}";
        }
    }
}
