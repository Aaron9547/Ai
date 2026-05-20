package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduledRunStatus {
    PENDING(0, "PENDING"),
    RUNNING(1, "RUNNING"),
    SUCCEEDED(2, "SUCCEEDED"),
    FAILED(3, "FAILED");

    @EnumValue
    private final int code;

    private final String storage;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
