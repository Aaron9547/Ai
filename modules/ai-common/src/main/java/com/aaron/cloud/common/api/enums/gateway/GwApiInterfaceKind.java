package com.aaron.cloud.common.api.enums.gateway;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 网关接口目录类型（接入方模块分组）。 */
@Getter
@RequiredArgsConstructor
public enum GwApiInterfaceKind {
    MODEL("MODEL"),
    ABILITY("ABILITY"),
    KNOWLEDGE("KNOWLEDGE"),
    OTHER("OTHER");

    @EnumValue
    private final String storage;
}
