package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduledRunTrigger {
    MANUAL("MANUAL"),
    CRON("CRON");

    @EnumValue
    private final String code;
}
