package com.aaron.cloud.identity.open;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/** 按租户配置发送注册验证码邮件。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenRegistrationVerificationSender {

    private final TenantAuthRegisterVerificationResolver verificationResolver;

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
        String subject = applyTemplate(email.getSubjectTemplate(), vars);
        String body = applyTemplate(email.getBodyTemplate(), vars);

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(email.getSmtpHost().trim());
        mailSender.setPort(email.getSmtpPort() > 0 ? email.getSmtpPort() : 465);
        mailSender.setUsername(blankToNull(email.getUsername()));
        mailSender.setPassword(blankToNull(email.getPassword()));
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (email.isSsl()) {
            mailSender.getJavaMailProperties().put("mail.smtp.auth", "true");
            mailSender.getJavaMailProperties().put("mail.smtp.ssl.enable", "true");
            mailSender.getJavaMailProperties().put("mail.smtp.socketFactory.port", String.valueOf(mailSender.getPort()));
            mailSender.getJavaMailProperties().put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            mailSender.getJavaMailProperties().put("mail.smtp.auth", "true");
            mailSender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
            mailSender.getJavaMailProperties().put("mail.smtp.starttls.required", "true");
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(email.getFrom().trim());
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
        log.debug("注册验证码邮件已发送 target={}", toEmail);
    }

    static String applyTemplate(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
