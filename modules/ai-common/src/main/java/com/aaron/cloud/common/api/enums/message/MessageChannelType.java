package com.aaron.cloud.common.api.enums.message;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageChannelType {
    EMAIL_SMTP("EMAIL_SMTP"),
    SMS_ALIYUN("SMS_ALIYUN"),
    SMS_TENCENT("SMS_TENCENT");

    @EnumValue
    private final String code;

    public static MessageChannelType fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("channelType required");
        }
        String n = raw.trim().toUpperCase();
        for (MessageChannelType e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown message channel type: " + raw);
    }
}
