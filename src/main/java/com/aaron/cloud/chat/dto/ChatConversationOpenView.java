package com.aaron.cloud.chat.dto;

import com.aaron.cloud.common.api.enums.chat.ConversationRecordStatus;
import com.aaron.cloud.common.chat.entity.ChatConversation;
import java.time.LocalDateTime;

/** 开放 API 会话视图：{@code id} 为 {@code public_id}，不含自增主键。 */
public record ChatConversationOpenView(
        String id, String title, ConversationRecordStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ChatConversationOpenView from(ChatConversation c) {
        return new ChatConversationOpenView(
                c.getPublicId(),
                c.getTitle(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
