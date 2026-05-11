package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对话意图处理器类型；管理端新建意图时选择其一，后端 {@link com.aaron.cloud.chat.intent.ChatIntentStreamRouter}
 * 将枚举映射到具体编排。新增类型时须同时实现处理器并注册。
 */
@Getter
@RequiredArgsConstructor
public enum ChatIntentHandlerKind {
    /** 出差报销多阶段（DOC→PLAN），与 ly-ai-application TravelReimbursementService 语义对齐；Coze 等走 extra_config_json。 */
    TRAVEL_REIMBURSEMENT(0);

    @EnumValue
    private final int code;
}
