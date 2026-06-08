package com.aaron.cloud.chat.intent;

/**
 * 一句话提醒处理器内固定轮次（与 {@code chat_intent_keyword.target_round}、消息 meta {@code intentFlowRound} 一致）。
 */
public enum OneSentenceReminderRound {
    /** 新建提醒（「提醒我…」） */
    CREATE,
    /** 取消提醒（「取消提醒」等关键词） */
    CANCEL,
    /** 续轮：用户按编号选择要取消的提醒 */
    CANCEL_SELECT
}
