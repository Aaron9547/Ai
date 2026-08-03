package com.aaron.cloud.notification.message.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageChannelConfigParser {

    private final ObjectMapper objectMapper;

    public SmtpChannelConfig parseSmtpConfig(String configJson) {
        return parse(configJson, SmtpChannelConfig.class, new SmtpChannelConfig());
    }

    public String parseSmtpPassword(String secretJson) {
        SmtpChannelSecret secret = parse(secretJson, SmtpChannelSecret.class, null);
        if (secret == null || secret.getPassword() == null) {
            return "";
        }
        return secret.getPassword();
    }

    public AliyunSmsChannelConfig parseAliyunConfig(String configJson) {
        return parse(configJson, AliyunSmsChannelConfig.class, new AliyunSmsChannelConfig());
    }

    public String parseAliyunSecret(String secretJson) {
        AliyunSmsChannelSecret secret = parse(secretJson, AliyunSmsChannelSecret.class, null);
        if (secret == null || secret.getAccessKeySecret() == null) {
            return "";
        }
        return secret.getAccessKeySecret();
    }

    public TencentSmsChannelConfig parseTencentConfig(String configJson) {
        return parse(configJson, TencentSmsChannelConfig.class, new TencentSmsChannelConfig());
    }

    public String parseTencentSecretId(String secretJson) {
        TencentSmsChannelSecret secret = parse(secretJson, TencentSmsChannelSecret.class, null);
        if (secret == null || secret.getSecretId() == null) {
            return "";
        }
        return secret.getSecretId();
    }

    public String parseTencentSecretKey(String secretJson) {
        TencentSmsChannelSecret secret = parse(secretJson, TencentSmsChannelSecret.class, null);
        if (secret == null || secret.getSecretKey() == null) {
            return "";
        }
        return secret.getSecretKey();
    }

    private <T> T parse(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            T parsed = objectMapper.readValue(json.trim(), type);
            return parsed == null ? fallback : parsed;
        } catch (Exception e) {
            return fallback;
        }
    }
}
