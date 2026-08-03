package com.aaron.cloud.common.api.enums.chat;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReminderScheduleType {
    ONCE(0),
    DAILY(1),
    WEEKLY(2),
    MONTHLY(3);

    @EnumValue
    private final int code;

    public static ReminderScheduleType fromMcpName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ReminderScheduleType.valueOf(raw.trim().toUpperCase());
    }
}
