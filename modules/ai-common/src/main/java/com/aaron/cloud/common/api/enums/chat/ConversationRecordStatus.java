package com.aaron.cloud.common.api.enums.chat;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConversationRecordStatus {
    ARCHIVED(0),
    ACTIVE(1);

    @EnumValue private final int code;
}
