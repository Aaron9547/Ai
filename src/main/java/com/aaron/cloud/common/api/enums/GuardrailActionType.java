package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuardrailActionType {
    LOG_ONLY(0),
    BLOCK(1),
    MASK(2);

    @EnumValue private final int code;
}
