package com.aaron.cloud.common.api.enums.chat;

/**
 * 单轮对话命中意图时的匹配来源（写入消息 meta JSON 的 {@code intentMatchSource}，与关键词 {@code hit_count} 累加条件对齐）。
 */
public enum ChatIntentMatchSource {
    /** 管理端配置的首轮触发关键词（子串包含），且对应 {@code chat_intent_keyword.id} 存在时累加 {@code hit_count} */
    TRIGGER_PHRASE,
    /** 管理端配置的取消类关键词（如「取消提醒」） */
    CANCEL_PHRASE,
    /** 取消提醒续轮：上一轮已展示编号清单后，用户仅回复序号 */
    CANCEL_INDEX_REPLY,
    /** 管理端配置的行程续办关键词 */
    PLAN_CONTINUE_PHRASE,
    /** 内置默认续办短语（无库 id，不累加 hit_count） */
    PLAN_CONTINUE_DEFAULT_PHRASE,
    /** 内置正则续办（无库 id） */
    PLAN_CONTINUE_REGEX,
    /** DOC 阶段用户携带附件继续（无关键词 id） */
    DOC_ATTACHMENT
}
