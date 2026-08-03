package com.aaron.cloud.common.api.enums.gateway;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 通用开关（启用/停用），用于配置行、限流规则等。 */
@Getter
@RequiredArgsConstructor
public enum ToggleState {
    OFF(0),
    ON(1);

    @EnumValue
    private final int code;
}
