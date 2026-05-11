package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 敏感词所属池：{@link #PLATFORM} 为全租户强制命中（{@code tenant_id} 固定为 0）；{@link #TENANT} 为单租户扩展词。
 */
@Getter
@RequiredArgsConstructor
public enum GuardrailSensitivePoolType {
    PLATFORM(0),
    TENANT(1);

    @EnumValue private final int code;
}
