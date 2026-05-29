package com.aaron.cloud.model.openai;

import com.aaron.cloud.common.api.dto.model.ModelToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 聚合 OpenAI SSE {@code delta.tool_calls} 分片。 */
public final class ToolCallDeltaAccumulator {

    private final Map<Integer, Partial> byIndex = new LinkedHashMap<>();

    public void accept(JsonNode toolCallsDelta) {
        if (toolCallsDelta == null || !toolCallsDelta.isArray()) {
            return;
        }
        for (JsonNode tc : toolCallsDelta) {
            int index = tc.has("index") ? tc.get("index").asInt(0) : byIndex.size();
            Partial p = byIndex.computeIfAbsent(index, k -> new Partial());
            if (tc.hasNonNull("id")) {
                p.id = tc.get("id").asText();
            }
            if (tc.hasNonNull("type")) {
                p.type = tc.get("type").asText();
            }
            JsonNode fn = tc.get("function");
            if (fn != null && fn.isObject()) {
                if (fn.hasNonNull("name")) {
                    p.functionName = fn.get("name").asText();
                }
                if (fn.hasNonNull("arguments")) {
                    p.arguments.append(fn.get("arguments").asText(""));
                }
            }
        }
    }

    public List<ModelToolCall> build() {
        List<ModelToolCall> out = new ArrayList<>();
        byIndex.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .forEach(e -> {
                    Partial p = e.getValue();
                    if (p.functionName == null || p.functionName.isBlank()) {
                        return;
                    }
                    out.add(ModelToolCall.builder()
                            .id(p.id)
                            .type(p.type == null ? "function" : p.type)
                            .function(ModelToolCall.FunctionCall.builder()
                                    .name(p.functionName)
                                    .arguments(p.arguments.toString())
                                    .build())
                            .build());
                });
        return out;
    }

    private static final class Partial {
        String id;
        String type;
        String functionName;
        final StringBuilder arguments = new StringBuilder();
    }
}
