package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 意图关键词语义：首轮触发 vs 行程阶段续办（与出差报销 PLAN 直达一致）。 */
@Getter
@RequiredArgsConstructor
public enum ChatIntentKeywordKind {
    TRIGGER(0),
    PLAN_CONTINUE(1);

    @EnumValue
    private final int code;
}
