package com.aaron.cloud.common.api.enums.message;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageDeliveryStatus {
    QUEUED(0),
    SENDING(1),
    SUCCEEDED(2),
    FAILED(3);

    @EnumValue
    private final int code;
}
