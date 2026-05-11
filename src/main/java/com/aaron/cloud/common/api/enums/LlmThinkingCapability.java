package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 是否支持向用户展示「思考」类流（依赖厂商 SSE 是否下发 reasoning 增量） */
@Getter
@RequiredArgsConstructor
public enum LlmThinkingCapability {
    NONE(0),
    SUPPORTED(1);

    @EnumValue private final int code;
}
