package com.aaron.cloud.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSharePublicView(
        String title, List<ChatMessageView> messages, LocalDateTime sharedAt, String conversationId) {}
