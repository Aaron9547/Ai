package com.aaron.cloud.common.api.enums.eval;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EvalRunStatus {
    QUEUED(0),
    RUNNING(1),
    SUCCEEDED(2),
    FAILED(3);

    @EnumValue private final int code;
}
