package com.aaron.cloud.notification.message.sender;

import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.notification.message.config.AliyunSmsChannelConfig;
import com.aaron.cloud.notification.message.config.MessageChannelConfigParser;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AliyunSmsMessageSender {

    private final MessageChannelConfigParser configParser;
    private final ObjectMapper objectMapper;

    public String send(MsgChannel channel, String phone, String templateBody, Map<String, String> templateVars)
            throws Exception {
        AliyunSmsChannelConfig cfg = configParser.parseAliyunConfig(channel.getConfigJson());
        String secret = configParser.parseAliyunSecret(channel.getSecretJson());
        if (!isReady(cfg, secret)) {
            throw new IllegalStateException("aliyun_sms_channel_not_ready");
        }
        Config clientCfg =
                new Config()
                        .setAccessKeyId(cfg.getAccessKeyId().trim())
                        .setAccessKeySecret(secret.trim())
                        .setEndpoint("dysmsapi.aliyuncs.com");
        Client client = new Client(clientCfg);
        SendSmsRequest req = new SendSmsRequest();
        req.setPhoneNumbers(phone);
        req.setSignName(cfg.getSignName().trim());
        req.setTemplateCode(cfg.getTemplateCode().trim());
        String paramJson =
                templateVars == null || templateVars.isEmpty()
                        ? "{}"
                        : objectMapper.writeValueAsString(templateVars);
        req.setTemplateParam(paramJson);
        SendSmsResponse resp = client.sendSms(req);
        if (resp == null || resp.getBody() == null) {
            throw new IllegalStateException("aliyun_sms_empty_response");
        }
        if (!"OK".equalsIgnoreCase(resp.getBody().getCode())) {
            throw new IllegalStateException(
                    "aliyun_sms_failed:" + resp.getBody().getCode() + ":" + resp.getBody().getMessage());
        }
        return resp.getBody().getBizId();
    }

    public static boolean isReady(AliyunSmsChannelConfig cfg, String secret) {
        return cfg.getAccessKeyId() != null
                && !cfg.getAccessKeyId().isBlank()
                && secret != null
                && !secret.isBlank()
                && cfg.getSignName() != null
                && !cfg.getSignName().isBlank()
                && cfg.getTemplateCode() != null
                && !cfg.getTemplateCode().isBlank();
    }
}
