package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatStarterEventType {
    IMPRESSION("IMPRESSION"),
    CLICK("CLICK"),
    SEND("SEND");

    @EnumValue
    private final String code;

    public static ChatStarterEventType fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("eventType required");
        }
        String n = raw.trim().toUpperCase();
        for (ChatStarterEventType e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown starter event type: " + raw);
    }
}
