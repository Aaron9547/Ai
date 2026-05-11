package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 未登录访客是否可在对话页选用该模型 */
@Getter
@RequiredArgsConstructor
public enum LlmAnonymousAccess {
    DISALLOWED(0),
    ALLOWED(1);

    @EnumValue private final int code;
}
