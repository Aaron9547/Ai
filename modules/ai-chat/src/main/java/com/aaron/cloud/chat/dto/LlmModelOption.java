package com.aaron.cloud.chat.dto;

/** C 端模型选择器展示项 */
public record LlmModelOption(
        String alias,
        String displayName,
        int maxAttachments,
        boolean supportsThinking,
        boolean allowAnonymous,
        /** 共用 token 额度已用尽（不可再起对话） */
        boolean quotaExhausted) {}
