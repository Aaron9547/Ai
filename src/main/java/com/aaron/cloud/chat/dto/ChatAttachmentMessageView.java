package com.aaron.cloud.chat.dto;

/**
 * 对话历史中用户消息携带的附件摘要（与 {@code chat_attachment} 行对应）。
 */
public record ChatAttachmentMessageView(
        long id, String fileName, Integer charLength, boolean textExtracted, String kind) {}
