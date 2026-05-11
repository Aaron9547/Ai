package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatMessageRole {
    SYSTEM(0),
    USER(1),
    ASSISTANT(2),
    TOOL(3);

    @EnumValue private final int code;
}
