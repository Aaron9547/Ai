package com.aaron.cloud.common.api.enums.chat;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 对话意图处理器类型；管理端新建意图时选择其一，后端 {@link com.aaron.cloud.chat.intent.ChatIntentStreamRouter}
 * 将枚举映射到具体编排。新增类型时须同时实现处理器并注册、补本枚举项与 {@link #adminLabelZh} 等展示字段。
 */
@Getter
@RequiredArgsConstructor
public enum ChatIntentHandlerKind {
    /**
     * 出差报销多阶段（DOC→PLAN），与 ly-ai-application TravelReimbursementService 语义对齐；可配置项见
     * {@link com.aaron.cloud.chat.intent.TravelReimbursementHandlerParam}。
     */
    TRAVEL_REIMBURSEMENT(
            0,
            "出差报销（材料与流程编排）",
            "多阶段 SSE（workflowStage）；先文档解析再行程规划；Coze 与材料策略由处理器参数枚举写入扩展 JSON。");

    @EnumValue
    private final int code;

    /** 管理端处理器下拉展示（勿写死在前端）。 */
    private final String adminLabelZh;

    private final String adminDescription;
}
