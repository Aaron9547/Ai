package com.aaron.cloud.common.tenant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 主对话：RAG / 联网注入体量，以及短期记忆（本会话历史条数/字数）预算（来自 {@code CHAT_PROMPT_LIMITS_JSON}，缺省字段用代码默认）。
 */
public record ChatPromptLimitsRuntime(
        int ragSnippetMaxChars,
        int ragMaxSnippets,
        int webSummaryMaxChars,
        int webMaxReferences,
        int webReferenceSnippetMaxChars,
        int webReferenceUrlMaxChars,
        int webGroundingTotalMaxChars,
        /** 注入模型上下文的「过去消息」条数上限（不含本轮 user）；按会话链接顺序取最近 N 条。 */
        int historyMaxMessages,
        /** 单条历史 user/assistant 正文截断上限（字符级，与 {@link com.aaron.cloud.common.util.TextClamp#ellipsis} 一致）。 */
        int historyMaxCharsPerMessage,
        /** 全部历史 turns 的正文总长上限；超出则从最早一条开始丢弃直至满足。 */
        int historyTotalMaxChars) {

    public static ChatPromptLimitsRuntime defaults() {
        return new ChatPromptLimitsRuntime(900, 3, 1600, 6, 280, 200, 7200, 40, 12_000, 48_000);
    }

    public static ChatPromptLimitsRuntime parse(String rawJson, ObjectMapper objectMapper) {
        if (rawJson == null || rawJson.isBlank()) {
            return defaults();
        }
        try {
            JsonNode n = objectMapper.readTree(rawJson);
            if (!n.isObject()) {
                return defaults();
            }
            ChatPromptLimitsRuntime d = defaults();
            return new ChatPromptLimitsRuntime(
                    intField(n, "ragSnippetMaxChars", d.ragSnippetMaxChars),
                    intField(n, "ragMaxSnippets", d.ragMaxSnippets),
                    intField(n, "webSummaryMaxChars", d.webSummaryMaxChars),
                    intField(n, "webMaxReferences", d.webMaxReferences),
                    intField(n, "webReferenceSnippetMaxChars", d.webReferenceSnippetMaxChars),
                    intField(n, "webReferenceUrlMaxChars", d.webReferenceUrlMaxChars),
                    intField(n, "webGroundingTotalMaxChars", d.webGroundingTotalMaxChars),
                    intField(n, "historyMaxMessages", d.historyMaxMessages),
                    intField(n, "historyMaxCharsPerMessage", d.historyMaxCharsPerMessage),
                    intField(n, "historyTotalMaxChars", d.historyTotalMaxChars));
        } catch (Exception ignored) {
            return defaults();
        }
    }

    private static int intField(JsonNode root, String name, int def) {
        JsonNode v = root.get(name);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asInt(def);
    }

    public int resolvedRagSnippetMaxChars() {
        return Math.clamp(ragSnippetMaxChars, 120, 16_000);
    }

    public int resolvedRagMaxSnippets() {
        return Math.clamp(ragMaxSnippets, 1, 12);
    }

    public int resolvedWebSummaryMaxChars() {
        return Math.clamp(webSummaryMaxChars, 200, 32_000);
    }

    public int resolvedWebMaxReferences() {
        return Math.clamp(webMaxReferences, 0, 24);
    }

    public int resolvedWebReferenceSnippetMaxChars() {
        return Math.clamp(webReferenceSnippetMaxChars, 40, 8_000);
    }

    public int resolvedWebReferenceUrlMaxChars() {
        return Math.clamp(webReferenceUrlMaxChars, 32, 2_048);
    }

    public int resolvedWebGroundingTotalMaxChars() {
        return Math.clamp(webGroundingTotalMaxChars, 400, 200_000);
    }

    /** 至少 0：0 表示不注入任何会话历史（仅本轮）。 */
    public int resolvedHistoryMaxMessages() {
        return Math.clamp(historyMaxMessages, 0, 200);
    }

    public int resolvedHistoryMaxCharsPerMessage() {
        return Math.clamp(historyMaxCharsPerMessage, 200, 128_000);
    }

    public int resolvedHistoryTotalMaxChars() {
        return Math.clamp(historyTotalMaxChars, 1_024, 500_000);
    }
}
