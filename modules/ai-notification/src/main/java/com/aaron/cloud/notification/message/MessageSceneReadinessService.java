package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.enums.message.MessageChannelStatus;
import com.aaron.cloud.common.api.enums.message.MessageChannelType;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.api.enums.message.MessageTemplateStatus;
import com.aaron.cloud.common.message.MsgChannelRepository;
import com.aaron.cloud.common.message.MsgTemplateRepository;
import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.common.message.entity.MsgTemplate;
import com.aaron.cloud.notification.message.config.AliyunSmsChannelConfig;
import com.aaron.cloud.notification.message.config.MessageChannelConfigParser;
import com.aaron.cloud.notification.message.config.SmtpChannelConfig;
import com.aaron.cloud.notification.message.config.TencentSmsChannelConfig;
import com.aaron.cloud.notification.message.sender.AliyunSmsMessageSender;
import com.aaron.cloud.notification.message.sender.SmtpMessageSender;
import com.aaron.cloud.notification.message.sender.TencentSmsMessageSender;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageSceneReadinessService {

    private final MsgTemplateRepository templateRepository;
    private final MsgChannelRepository channelRepository;
    private final MessageChannelConfigParser configParser;

    public boolean isSceneDeliveryReady(long tenantId, com.aaron.cloud.common.api.enums.message.MessageSceneCode scene) {
        return templateRepository
                .findActiveByScene(tenantId, scene, "zh-CN")
                .flatMap(t -> channelRepository.findById(t.getChannelId(), tenantId))
                .filter(ch -> ch.getStatus() == MessageChannelStatus.ACTIVE)
                .map(this::isChannelReady)
                .orElse(false);
    }

    public boolean isChannelReady(MsgChannel channel) {
        if (channel == null || channel.getStatus() != MessageChannelStatus.ACTIVE) {
            return false;
        }
        return switch (channel.getChannelType()) {
            case EMAIL_SMTP -> {
                SmtpChannelConfig cfg = configParser.parseSmtpConfig(channel.getConfigJson());
                yield SmtpMessageSender.isReady(cfg, configParser.parseSmtpPassword(channel.getSecretJson()));
            }
            case SMS_ALIYUN -> {
                AliyunSmsChannelConfig cfg = configParser.parseAliyunConfig(channel.getConfigJson());
                yield AliyunSmsMessageSender.isReady(cfg, configParser.parseAliyunSecret(channel.getSecretJson()));
            }
            case SMS_TENCENT -> {
                TencentSmsChannelConfig cfg = configParser.parseTencentConfig(channel.getConfigJson());
                yield TencentSmsMessageSender.isReady(
                        cfg,
                        configParser.parseTencentSecretId(channel.getSecretJson()),
                        configParser.parseTencentSecretKey(channel.getSecretJson()));
            }
        };
    }

    public ResolvedMessageRoute resolve(long tenantId, com.aaron.cloud.common.api.enums.message.MessageSceneCode scene) {
        MsgTemplate template =
                templateRepository
                        .findActiveByScene(tenantId, scene, "zh-CN")
                        .orElseThrow(() -> new IllegalStateException("message_template_not_found"));
        if (template.getStatus() != MessageTemplateStatus.ACTIVE) {
            throw new IllegalStateException("message_template_disabled");
        }
        MsgChannel channel =
                channelRepository
                        .findById(template.getChannelId(), tenantId)
                        .orElseThrow(() -> new IllegalStateException("message_channel_not_found"));
        if (channel.getStatus() != MessageChannelStatus.ACTIVE) {
            throw new IllegalStateException("message_channel_disabled");
        }
        if (!isChannelReady(channel)) {
            throw new IllegalStateException("message_channel_not_ready");
        }
        return new ResolvedMessageRoute(template, channel);
    }

    public record ResolvedMessageRoute(MsgTemplate template, MsgChannel channel) {}

    public record RenderedMessage(String subject, String body) {

        public static RenderedMessage render(MsgTemplate template, MsgChannel channel, Map<String, String> vars) {
            String subject =
                    MessageTemplateSupport.applyTemplate(
                            template.getSubjectTemplate() == null ? "" : template.getSubjectTemplate(), vars);
            String body = MessageTemplateSupport.applyTemplate(template.getBodyTemplate(), vars);
            if (channel.getChannelType() != MessageChannelType.EMAIL_SMTP) {
                subject = "";
            }
            return new RenderedMessage(subject, body);
        }
    }
}
