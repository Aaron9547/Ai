package com.aaron.cloud.chat.dto;

/** 会话内按计量场景拆分的 token 用量。 */
public record ConversationTokenSceneView(
        String scene,
        String label,
        int totalTokens,
        int promptTokens,
        int completionTokens) {}
