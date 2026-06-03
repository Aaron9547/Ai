package com.aaron.cloud.common.api.enums.chat;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatUserReminderStatus {
    ACTIVE(0),
    CANCELLED(1),
    EXPIRED(2);

    @EnumValue
    private final int code;
}
