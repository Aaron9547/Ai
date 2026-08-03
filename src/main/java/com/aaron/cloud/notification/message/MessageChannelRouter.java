package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.enums.message.MessageChannelType;
import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.notification.message.sender.AliyunSmsMessageSender;
import com.aaron.cloud.notification.message.sender.SmtpMessageSender;
import com.aaron.cloud.notification.message.sender.TencentSmsMessageSender;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageChannelRouter {

    private final SmtpMessageSender smtpMessageSender;
    private final AliyunSmsMessageSender aliyunSmsMessageSender;
    private final TencentSmsMessageSender tencentSmsMessageSender;

    public String send(
            MsgChannel channel,
            String recipient,
            String subject,
            String body,
            Map<String, String> templateVars)
            throws Exception {
        return switch (channel.getChannelType()) {
            case EMAIL_SMTP -> smtpMessageSender.send(channel, recipient, subject, body);
            case SMS_ALIYUN -> aliyunSmsMessageSender.send(channel, recipient, body, templateVars);
            case SMS_TENCENT -> tencentSmsMessageSender.send(channel, recipient, body, templateVars);
        };
    }

    public MessageChannelType channelType(MsgChannel channel) {
        return channel.getChannelType();
    }
}
