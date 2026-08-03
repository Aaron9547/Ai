package com.aaron.cloud.model.openai;

import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.fasterxml.jackson.databind.JsonNode;

public final class OpenAiUsageParser {

    private OpenAiUsageParser() {}

    /** 解析 OpenAI 兼容 {@code usage} 对象（{@code snake_case} 为主，部分网关返回 {@code camelCase}）。 */
    public static ModelTokenUsage parse(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return new ModelTokenUsage(0, 0, 0);
        }
        int p = pickInt(usage, "prompt_tokens", "promptTokens");
        int c = pickInt(usage, "completion_tokens", "completionTokens");
        int t;
        if (usage.has("total_tokens") && !usage.get("total_tokens").isNull()) {
            t = usage.get("total_tokens").asInt(0);
        } else if (usage.has("totalTokens") && !usage.get("totalTokens").isNull()) {
            t = usage.get("totalTokens").asInt(0);
        } else {
            t = Math.max(0, p) + Math.max(0, c);
        }
        return new ModelTokenUsage(Math.max(0, p), Math.max(0, c), Math.max(0, t));
    }

    private static int pickInt(JsonNode usage, String snake, String camel) {
        if (usage.has(snake) && !usage.get(snake).isNull()) {
            return usage.get(snake).asInt(0);
        }
        if (usage.has(camel) && !usage.get(camel).isNull()) {
            return usage.get(camel).asInt(0);
        }
        return 0;
    }
}
