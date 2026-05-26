package com.aaron.cloud.identity.mail;

import com.aaron.cloud.identity.open.AuthRegisterVerificationConfig.EmailChannel;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class TenantJavaMailSenderFactory {

    public JavaMailSenderImpl create(EmailChannel email) {
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
        return mailSender;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
