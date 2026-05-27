package com.aaron.cloud.common.api.enums.message;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageChannelStatus {
    DISABLED(0),
    ACTIVE(1);

    @EnumValue
    private final int code;
}
