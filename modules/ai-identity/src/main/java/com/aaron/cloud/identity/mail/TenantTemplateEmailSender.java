package com.aaron.cloud.identity.mail;

import com.aaron.cloud.identity.open.AuthRegisterEmailSupport;
import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig.EmailChannel;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantTemplateEmailSender {

    private final TenantJavaMailSenderFactory mailSenderFactory;

    public void send(EmailChannel email, String to, String subject, String body) {
        if (!AuthRegisterEmailSupport.isDeliveryReady(email)) {
            throw new IllegalStateException("email_delivery_not_configured");
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(email.getFrom().trim());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSenderFactory.create(email).send(msg);
        log.debug("tenant template email sent target={}", to);
    }

    public static String applyTemplate(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }
}
