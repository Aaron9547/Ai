package com.aaron.cloud.notification.message.sender;

import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.notification.message.MessageTemplateSupport;
import com.aaron.cloud.notification.message.config.MessageChannelConfigParser;
import com.aaron.cloud.notification.message.config.SmtpChannelConfig;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        try {
            MimeMessage mime = mailSenderFactory.create(cfg, password).createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(cfg.getFrom().trim());
            helper.setTo(to);
            helper.setSubject(subject == null ? "" : subject);
            String plain = MessageTemplateSupport.normalizeEscapes(body == null ? "" : body);
            if (MessageTemplateSupport.looksLikeHtml(plain)) {
                helper.setText(MessageTemplateSupport.htmlToPlainFallback(plain), plain);
            } else {
                helper.setText(plain, MessageTemplateSupport.plainTextToSimpleHtml(plain));
            }
            mailSenderFactory.create(cfg, password).send(mime);
            return "";
        } catch (Exception ex) {
            throw new IllegalStateException("smtp_send_failed: " + ex.getMessage(), ex);
        }
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
