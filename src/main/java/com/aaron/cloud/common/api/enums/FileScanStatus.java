package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileScanStatus {
    UNKNOWN(0),
    PENDING(1),
    CLEAN(2),
    INFECTED(3);

    @EnumValue private final int code;
}
