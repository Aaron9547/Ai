package com.aaron.cloud.chat.dto;

import java.util.List;

/** 开放 API：会话内全部 LLM 调用的 token 计量合计与场景拆分。 */
public record ConversationTokenTotalView(
        int totalTokens, int promptTokens, int completionTokens, List<ConversationTokenSceneView> byScene) {

    public ConversationTokenTotalView {
        byScene = byScene == null ? List.of() : List.copyOf(byScene);
    }
}
