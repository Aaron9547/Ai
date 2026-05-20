package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatStarterDailyBatchStatus {
    PENDING("PENDING"),
    OK("OK"),
    FAILED("FAILED");

    @EnumValue
    private final String code;

    public static ChatStarterDailyBatchStatus fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status required");
        }
        String n = raw.trim().toUpperCase();
        for (ChatStarterDailyBatchStatus e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown daily batch status: " + raw);
    }
}
