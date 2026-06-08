package com.aaron.cloud.common.tenant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RAG 检索调优（改写语义门控、简单/复杂分流、混合 LTR）；来自 {@code RAG_RETRIEVAL_TUNING_JSON}，缺省字段用代码默认。
 */
public record RagRetrievalTuningRuntime(
        boolean rewriteEnabled,
        double rewriteSemanticMinSimilarity,
        String rewriteModelId,
        int rewriteContextMaxChars,
        boolean simpleQueryFastPathEnabled,
        int simpleQueryMaxChars,
        boolean hybridLtrEnabled,
        int ltrCandidateMultiplier,
        Long activeLtrFileObjectId,
        Long activeLtrJobTaskId,
        String ltrModelVersion) {

    public static RagRetrievalTuningRuntime defaults() {
        return new RagRetrievalTuningRuntime(
                true, 0.80d, null, 2000, true, 32, true, 5, null, null, "builtin-v1");
    }

    public static RagRetrievalTuningRuntime parse(String rawJson, ObjectMapper objectMapper) {
        if (rawJson == null || rawJson.isBlank()) {
            return defaults();
        }
        try {
            JsonNode n = objectMapper.readTree(rawJson);
            if (!n.isObject()) {
                return defaults();
            }
            RagRetrievalTuningRuntime d = defaults();
            return new RagRetrievalTuningRuntime(
                    boolField(n, "rewriteEnabled", d.rewriteEnabled),
                    doubleField(n, "rewriteSemanticMinSimilarity", d.rewriteSemanticMinSimilarity),
                    textField(n, "rewriteModelId", d.rewriteModelId),
                    intField(n, "rewriteContextMaxChars", d.rewriteContextMaxChars),
                    boolField(n, "simpleQueryFastPathEnabled", d.simpleQueryFastPathEnabled),
                    intField(n, "simpleQueryMaxChars", d.simpleQueryMaxChars),
                    boolField(n, "hybridLtrEnabled", d.hybridLtrEnabled),
                    intField(n, "ltrCandidateMultiplier", d.ltrCandidateMultiplier),
                    longFieldOrNull(n, "activeLtrFileObjectId"),
                    longFieldOrNull(n, "activeLtrJobTaskId"),
                    textField(n, "ltrModelVersion", d.ltrModelVersion));
        } catch (Exception ignored) {
            return defaults();
        }
    }

    /** 租户 JSON 覆盖进程 yml 默认（yml 非空字段优先作为基线再被租户覆盖）。 */
    public static RagRetrievalTuningRuntime merge(
            RagRetrievalTuningRuntime ymlBaseline, RagRetrievalTuningRuntime tenantParsed) {
        RagRetrievalTuningRuntime y = ymlBaseline == null ? defaults() : ymlBaseline;
        RagRetrievalTuningRuntime t = tenantParsed == null ? defaults() : tenantParsed;
        return new RagRetrievalTuningRuntime(
                t.rewriteEnabled,
                t.rewriteSemanticMinSimilarity,
                t.rewriteModelId != null ? t.rewriteModelId : y.rewriteModelId,
                t.rewriteContextMaxChars,
                t.simpleQueryFastPathEnabled,
                t.simpleQueryMaxChars,
                t.hybridLtrEnabled,
                t.ltrCandidateMultiplier,
                t.activeLtrFileObjectId != null ? t.activeLtrFileObjectId : y.activeLtrFileObjectId,
                t.activeLtrJobTaskId != null ? t.activeLtrJobTaskId : y.activeLtrJobTaskId,
                t.ltrModelVersion != null && !t.ltrModelVersion.isBlank()
                        ? t.ltrModelVersion
                        : y.ltrModelVersion);
    }

    public double resolvedRewriteSemanticMinSimilarity() {
        return Math.clamp(rewriteSemanticMinSimilarity, 0.5d, 0.99d);
    }

    public int resolvedRewriteContextMaxChars() {
        return Math.clamp(rewriteContextMaxChars, 200, 16_000);
    }

    public int resolvedSimpleQueryMaxChars() {
        return Math.clamp(simpleQueryMaxChars, 8, 128);
    }

    public int resolvedLtrCandidateMultiplier() {
        return Math.clamp(ltrCandidateMultiplier, 2, 20);
    }

    private static int intField(JsonNode root, String name, int def) {
        JsonNode v = root.get(name);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asInt(def);
    }

    private static double doubleField(JsonNode root, String name, double def) {
        JsonNode v = root.get(name);
        if (v == null || !v.isNumber()) {
            return def;
        }
        return v.asDouble(def);
    }

    private static boolean boolField(JsonNode root, String name, boolean def) {
        JsonNode v = root.get(name);
        if (v == null) {
            return def;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.isTextual()) {
            return "true".equalsIgnoreCase(v.asText().trim());
        }
        return def;
    }

    private static String textField(JsonNode root, String name, String def) {
        JsonNode v = root.get(name);
        if (v == null || v.isNull()) {
            return def;
        }
        if (v.isTextual()) {
            String t = v.asText().trim();
            return t.isEmpty() ? def : t;
        }
        return def;
    }

    private static Long longFieldOrNull(JsonNode root, String name) {
        JsonNode v = root.get(name);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asLong();
        }
        if (v.isTextual() && !v.asText().isBlank()) {
            try {
                return Long.parseLong(v.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
