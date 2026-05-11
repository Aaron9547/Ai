package com.aaron.cloud.common.api.enums;

/** 助手消息的用户评价（持久化在 {@code chat_message.meta_json#userFeedback}）。 */
public enum ChatMessageUserFeedback {
    NONE,
    /** 正向反馈（RLHF 等用途） */
    LIKE,
    DISLIKE
}
