package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import java.util.ArrayList;
import java.util.List;

/** 火山 Ark 联网 Bot 调用入参（多轮 messages，与固定源关键词渠道分离）。 */
public record WebSearchArkInvokeRequest(
        String logSummary, List<ModelChatRequest.MessageTurn> messages) {

    public WebSearchArkInvokeRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        logSummary = logSummary == null ? "" : logSummary.trim();
    }

    public static WebSearchArkInvokeRequest singleUser(String userText) {
        String t = userText == null ? "" : userText.trim();
        var user = new ModelChatRequest.MessageTurn();
        user.setRole("user");
        user.setContent(t);
        return new WebSearchArkInvokeRequest(t, List.of(user));
    }
}
