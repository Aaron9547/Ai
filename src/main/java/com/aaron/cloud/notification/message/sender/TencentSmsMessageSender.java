package com.aaron.cloud.notification.message.sender;

import com.aaron.cloud.common.message.entity.MsgChannel;
import com.aaron.cloud.notification.message.MessageTemplateSupport;
import com.aaron.cloud.notification.message.config.MessageChannelConfigParser;
import com.aaron.cloud.notification.message.config.TencentSmsChannelConfig;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TencentSmsMessageSender {

    private final MessageChannelConfigParser configParser;

    public String send(MsgChannel channel, String phone, String templateBody, Map<String, String> templateVars)
            throws Exception {
        TencentSmsChannelConfig cfg = configParser.parseTencentConfig(channel.getConfigJson());
        String secretId = configParser.parseTencentSecretId(channel.getSecretJson());
        String secretKey = configParser.parseTencentSecretKey(channel.getSecretJson());
        if (!isReady(cfg, secretId, secretKey)) {
            throw new IllegalStateException("tencent_sms_channel_not_ready");
        }
        Credential cred = new Credential(secretId.trim(), secretKey.trim());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("sms.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        SmsClient client = new SmsClient(cred, cfg.getRegion(), clientProfile);
        SendSmsRequest req = new SendSmsRequest();
        req.setSmsSdkAppId(cfg.getSdkAppId().trim());
        req.setSignName(cfg.getSignName().trim());
        req.setTemplateId(cfg.getTemplateId().trim());
        req.setPhoneNumberSet(new String[] {normalizePhone(phone)});
        req.setTemplateParamSet(buildTemplateParams(templateBody, templateVars));
        SendSmsResponse resp = client.SendSms(req);
        if (resp == null || resp.getSendStatusSet() == null || resp.getSendStatusSet().length == 0) {
            throw new IllegalStateException("tencent_sms_empty_response");
        }
        SendStatus status = resp.getSendStatusSet()[0];
        if (!"Ok".equalsIgnoreCase(status.getCode())) {
            throw new IllegalStateException("tencent_sms_failed:" + status.getCode() + ":" + status.getMessage());
        }
        return status.getSerialNo();
    }

    private static String[] buildTemplateParams(String templateBody, Map<String, String> templateVars) {
        String rendered = MessageTemplateSupport.applyTemplate(templateBody, templateVars);
        if (rendered == null || rendered.isBlank()) {
            return new String[] {""};
        }
        if (templateVars != null && !templateVars.isEmpty()) {
            List<String> ordered = new ArrayList<>(templateVars.values());
            return ordered.toArray(new String[0]);
        }
        return new String[] {rendered};
    }

    private static String normalizePhone(String phone) {
        String p = phone == null ? "" : phone.trim();
        if (p.startsWith("+")) {
            return p;
        }
        if (p.startsWith("86") && p.length() > 11) {
            return "+" + p;
        }
        return "+86" + p;
    }

    public static boolean isReady(TencentSmsChannelConfig cfg, String secretId, String secretKey) {
        return cfg.getSdkAppId() != null
                && !cfg.getSdkAppId().isBlank()
                && secretId != null
                && !secretId.isBlank()
                && secretKey != null
                && !secretKey.isBlank()
                && cfg.getSignName() != null
                && !cfg.getSignName().isBlank()
                && cfg.getTemplateId() != null
                && !cfg.getTemplateId().isBlank();
    }
}
