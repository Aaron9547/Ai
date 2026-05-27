package com.aaron.cloud.notification.message.sender;

import com.aaron.cloud.notification.message.config.SmtpChannelConfig;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class SmtpJavaMailSenderFactory {

    public JavaMailSenderImpl create(SmtpChannelConfig config, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost().trim());
        mailSender.setPort(config.getSmtpPort() > 0 ? config.getSmtpPort() : 465);
        mailSender.setUsername(blankToNull(config.getUsername()));
        mailSender.setPassword(blankToNull(password));
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (config.isSsl()) {
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
