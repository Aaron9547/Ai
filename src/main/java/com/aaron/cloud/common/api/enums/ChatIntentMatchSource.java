package com.aaron.cloud.common.api.enums;

/**
 * 单轮对话命中意图时的匹配来源（写入消息 meta JSON 的 {@code intentMatchSource}，与关键词 {@code hit_count} 累加条件对齐）。
 */
public enum ChatIntentMatchSource {
    /** 管理端配置的首轮触发关键词（子串包含），且对应 {@code chat_intent_keyword.id} 存在时累加 {@code hit_count} */
    TRIGGER_PHRASE,
    /** 管理端配置的行程续办关键词 */
    PLAN_CONTINUE_PHRASE,
    /** 内置默认续办短语（无库 id，不累加 hit_count） */
    PLAN_CONTINUE_DEFAULT_PHRASE,
    /** 内置正则续办（无库 id） */
    PLAN_CONTINUE_REGEX,
    /** DOC 阶段用户携带附件继续（无关键词 id） */
    DOC_ATTACHMENT
}
