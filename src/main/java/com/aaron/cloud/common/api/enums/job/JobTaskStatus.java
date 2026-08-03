package com.aaron.cloud.common.api.enums.job;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobTaskStatus {
    PENDING(0),
    RUNNING(1),
    SUCCEEDED(2),
    FAILED(3),
    CANCELLED(4);

    @EnumValue private final int code;
}
