package com.aaron.cloud.common.api.enums.profile;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** {@code ten_user_weekly_insight.status} */
@Getter
@RequiredArgsConstructor
public enum KnowledgeWeeklyInsightStatus {
    DRAFT(0, "DRAFT"),
    READY(1, "READY"),
    SENT(2, "SENT"),
    SKIPPED(3, "SKIPPED"),
    FAILED(4, "FAILED");

    @EnumValue
    private final int code;

    private final String storage;
}
