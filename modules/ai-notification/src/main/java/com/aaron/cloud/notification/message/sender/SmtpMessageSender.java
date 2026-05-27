package com.aaron.cloud.notification.message.sender;

import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.notification.message.config.MessageChannelConfigParser;
import com.aaron.cloud.notification.message.config.SmtpChannelConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpMessageSender {

    private final MessageChannelConfigParser configParser;
    private final SmtpJavaMailSenderFactory mailSenderFactory;

    public String send(MsgChannel channel, String to, String subject, String body) {
        SmtpChannelConfig cfg = configParser.parseSmtpConfig(channel.getConfigJson());
        String password = configParser.parseSmtpPassword(channel.getSecretJson());
        if (!isReady(cfg, password)) {
            throw new IllegalStateException("smtp_channel_not_ready");
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(cfg.getFrom().trim());
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSenderFactory.create(cfg, password).send(msg);
        return "";
    }

    public static boolean isReady(SmtpChannelConfig cfg, String password) {
        return cfg.getSmtpHost() != null
                && !cfg.getSmtpHost().isBlank()
                && cfg.getFrom() != null
                && !cfg.getFrom().isBlank()
                && cfg.getUsername() != null
                && !cfg.getUsername().isBlank()
                && password != null
                && !password.isBlank();
    }
}
