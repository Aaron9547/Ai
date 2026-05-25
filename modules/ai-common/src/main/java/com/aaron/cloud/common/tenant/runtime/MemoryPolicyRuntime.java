package com.aaron.cloud.common.tenant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 用户记忆策略中随租户可调部分（来自 {@code MEMORY_POLICY_JSON}）；Milvus 开关与队列名等仍见 {@code ai.memory} yml。
 */
public record MemoryPolicyRuntime(
        boolean abstractRefreshEnabled,
        int abstractChunkWindow,
        boolean abstractSyncFallback,
        long abstractDedupeTtlSeconds,
        boolean enqueueAbstractOnConversationCreate,
        int promptAbstractBodyMaxChars,
        int promptConcreteChunkLimit,
        int promptConcreteChunkMaxChars,
        int vectorSearchTopK) {

    private static final int SNIPPET_STORE_MAX = 1800;

    public static MemoryPolicyRuntime defaults() {
        return new MemoryPolicyRuntime(true, 24, true, 45L, false, 2800, 4, 420, 8);
    }

    public static MemoryPolicyRuntime parse(String rawJson, ObjectMapper objectMapper) {
        if (rawJson == null || rawJson.isBlank()) {
            return defaults();
        }
        try {
            JsonNode n = objectMapper.readTree(rawJson);
            if (!n.isObject()) {
                return defaults();
            }
            MemoryPolicyRuntime d = defaults();
            return new MemoryPolicyRuntime(
                    boolField(n, "abstractRefreshEnabled", d.abstractRefreshEnabled),
                    intField(n, "abstractChunkWindow", d.abstractChunkWindow),
                    boolField(n, "abstractSyncFallback", d.abstractSyncFallback),
                    longField(n, "abstractDedupeTtlSeconds", d.abstractDedupeTtlSeconds),
                    boolField(n, "enqueueAbstractOnConversationCreate", d.enqueueAbstractOnConversationCreate),
                    intField(n, "promptAbstractBodyMaxChars", d.promptAbstractBodyMaxChars),
                    intField(n, "promptConcreteChunkLimit", d.promptConcreteChunkLimit),
                    intField(n, "promptConcreteChunkMaxChars", d.promptConcreteChunkMaxChars),
                    intField(n, "vectorSearchTopK", d.vectorSearchTopK));
        } catch (Exception ignored) {
            return defaults();
        }
    }

    private static boolean boolField(JsonNode root, String name, boolean def) {
        JsonNode v = root.get(name);
        if (v == null || v.isNull()) {
            return def;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isTextual()) {
            return Boolean.parseBoolean(v.asText().trim());
        }
        return def;
    }

    private static int intField(JsonNode root, String name, int def) {
        JsonNode v = root.get(name);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asInt(def);
    }

    private static long longField(JsonNode root, String name, long def) {
        JsonNode v = root.get(name);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asLong(def);
    }

    public int resolvedPromptAbstractBodyMaxChars() {
        int v = promptAbstractBodyMaxChars;
        if (v <= 0) {
            return 2800;
        }
        return Math.min(v, 100_000);
    }

    public int resolvedPromptConcreteChunkLimit() {
        int v = promptConcreteChunkLimit;
        if (v <= 0) {
            return 4;
        }
        return Math.min(v, 24);
    }

    public int resolvedPromptConcreteChunkMaxChars() {
        int v = promptConcreteChunkMaxChars;
        if (v <= 0) {
            return 420;
        }
        return Math.clamp(v, 80, SNIPPET_STORE_MAX);
    }

    /** 与 {@link com.aaron.cloud.common.profile.UserMemoryApplicationService#recallConcreteChunks} 中 Milvus 召回 topK 对齐。 */
    public int resolvedVectorSearchTopK() {
        int v = vectorSearchTopK;
        if (v <= 0) {
            return 8;
        }
        return Math.min(v, 50);
    }

    public long resolvedAbstractDedupeTtlSeconds() {
        long v = abstractDedupeTtlSeconds;
        if (v <= 0) {
            return 45L;
        }
        return Math.max(5L, Math.min(v, 86_400L));
    }

    public int resolvedAbstractChunkWindow() {
        int v = abstractChunkWindow;
        if (v <= 0) {
            v = 24;
        }
        return Math.max(4, Math.min(v, 80));
    }
}
