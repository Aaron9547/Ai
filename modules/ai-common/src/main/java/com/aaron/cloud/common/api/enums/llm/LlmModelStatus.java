package com.aaron.cloud.common.api.enums.llm;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LlmModelStatus {
    DISABLED(0),
    ACTIVE(1);

    @EnumValue private final int code;
}
