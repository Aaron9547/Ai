package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 联网编排：Ark 与固定源关键词 LLM 共用 {@link #recentHistoryForArk()}（近 1～2 轮 user/assistant，无画像）；
 * 当前轮正文为 {@link #keywordSourceText()}。
 */
public record WebSearchUserContext(
        String keywordSourceText, List<ModelChatRequest.MessageTurn> recentHistoryForArk) {

    public WebSearchUserContext {
        keywordSourceText = keywordSourceText == null ? "" : keywordSourceText.trim();
        recentHistoryForArk =
                recentHistoryForArk == null ? List.of() : List.copyOf(recentHistoryForArk);
    }

    public static WebSearchUserContext of(String currentTurnText) {
        return new WebSearchUserContext(currentTurnText, List.of());
    }

    /**
     * 对话联网：Ark 注入本轮 user 之前最近 {@code maxTurnPairs} 轮 user/assistant（不含画像/RAG）。
     */
    public static WebSearchUserContext forConversation(
            String currentTurnText,
            List<ModelChatRequest.MessageTurn> historyTurns,
            int maxTurnPairs) {
        return new WebSearchUserContext(currentTurnText, tailHistoryForArk(historyTurns, maxTurnPairs));
    }

    /** @deprecated 使用 {@link #of(String)} */
    @Deprecated
    public static WebSearchUserContext messageOnly(String currentTurnText) {
        return of(currentTurnText);
    }

    public WebSearchArkInvokeRequest arkInvokeRequest(String roundSuffix) {
        return WebSearchArkContextBuilder.toInvokeRequest(
                keywordSourceText,
                roundSuffix == null ? "" : roundSuffix,
                recentHistoryForArk);
    }

    public int arkMessageCount() {
        return WebSearchArkContextBuilder.build(keywordSourceText, "", recentHistoryForArk).size();
    }

    /** 取历史尾部最多 {@code maxTurnPairs} 轮（每轮 user+assistant 最多 2 条，共最多 4 条）。 */
    static List<ModelChatRequest.MessageTurn> tailHistoryForArk(
            List<ModelChatRequest.MessageTurn> historyTurns, int maxTurnPairs) {
        if (historyTurns == null || historyTurns.isEmpty() || maxTurnPairs <= 0) {
            return List.of();
        }
        int maxMessages = Math.min(Math.max(1, maxTurnPairs) * 2, historyTurns.size());
        int from = historyTurns.size() - maxMessages;
        var out = new ArrayList<ModelChatRequest.MessageTurn>(maxMessages);
        for (int i = from; i < historyTurns.size(); i++) {
            ModelChatRequest.MessageTurn t = historyTurns.get(i);
            if (t == null || t.getRole() == null) {
                continue;
            }
            String role = t.getRole().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            out.add(t);
        }
        return List.copyOf(out);
    }
}
