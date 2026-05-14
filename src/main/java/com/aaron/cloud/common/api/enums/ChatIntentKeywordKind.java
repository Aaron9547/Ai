package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 意图关键词语义：首轮触发 vs 行程阶段续办等。
 *
 * <p>多轮流处理器（如 {@code TRAVEL_REIMBURSEMENT}）以代码内枚举定义轮次；库表 {@code chat_intent_keyword.target_round}
 * 可显式绑定短语到某轮（为空时由处理器按 kind 推断，例如 {@link #PLAN_CONTINUE} 默认对应差旅 {@code PLAN} 轮）。
 */
@Getter
@RequiredArgsConstructor
public enum ChatIntentKeywordKind {
    TRIGGER(0),
    PLAN_CONTINUE(1);

    @EnumValue
    private final int code;
}
