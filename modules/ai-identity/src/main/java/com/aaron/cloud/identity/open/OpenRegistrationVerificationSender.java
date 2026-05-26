package com.aaron.cloud.identity.open;

import com.aaron.cloud.identity.mail.TenantTemplateEmailSender;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 按租户配置发送注册验证码邮件。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRegistrationVerificationSender {

    private final TenantAuthRegisterVerificationResolver verificationResolver;
    private final TenantTemplateEmailSender templateEmailSender;

    public void sendRegisterCode(
            long tenantId, String tenantName, String toEmail, String code, long ttlMinutes) {
        AuthRegisterVerificationConfig cfg = verificationResolver.resolve(tenantId);
        AuthRegisterVerificationConfig.EmailChannel email = cfg.getEmail();
        if (!AuthRegisterEmailSupport.isDeliveryReady(email)) {
            throw new IllegalStateException("register_email_delivery_not_configured");
        }
        sendEmail(email, tenantName, toEmail, code, ttlMinutes);
    }

    private void sendEmail(
            AuthRegisterVerificationConfig.EmailChannel email,
            String tenantName,
            String toEmail,
            String code,
            long ttlMinutes) {
        Map<String, String> vars =
                Map.of(
                        "code", code,
                        "ttlMinutes", String.valueOf(ttlMinutes),
                        "tenantName", tenantName == null ? "" : tenantName,
                        "email", toEmail);
        String subject = TenantTemplateEmailSender.applyTemplate(email.getSubjectTemplate(), vars);
        String body = TenantTemplateEmailSender.applyTemplate(email.getBodyTemplate(), vars);
        templateEmailSender.send(email, toEmail, subject, body);
        log.debug("注册验证码邮件已发送 target={}", toEmail);
    }
}
