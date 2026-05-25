package com.aaron.cloud.chat.intent.coze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 解析 Coze {@code /v1/workflow/stream_run} SSE 聚合缓冲中的正文，语义对齐 ly
 * {@code TravelReimbursementService#extractWorkflowOutput}（Jackson 版）。
 */
public final class TravelCozeResponseParser {

    private TravelCozeResponseParser() {}

    public static String extractWorkflowOutput(ObjectMapper mapper, String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "";
        }
        String raw = rawResponse.trim();
        String endContent = extractEndNodeContent(mapper, raw);
        if (endContent != null && !endContent.isBlank()) {
            return endContent;
        }
        String output = tryExtractOutputFromJson(mapper, raw);
        if (output != null && !output.isBlank()) {
            return output;
        }
        String sseExtracted = extractLastDataJson(raw);
        if (sseExtracted != null && !sseExtracted.isBlank()) {
            if (isCozeStreamDebugEnvelope(mapper, sseExtracted)) {
                return "";
            }
            output = tryExtractOutputFromJson(mapper, sseExtracted);
            if (output != null && !output.isBlank()) {
                return output;
            }
            return sseExtracted;
        }
        if (isCozeStreamDebugEnvelope(mapper, raw)) {
            return "";
        }
        return raw;
    }

    /**
     * Coze 流式帧中常见仅含 {@code debug_url} / {@code node_execute_uuid} 等排障字段、无业务 {@code output} 的 JSON，
     * 与 ly Fastjson 路径一致须过滤，避免当作正文回显。
     */
    static boolean isCozeStreamDebugEnvelope(ObjectMapper mapper, String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            return false;
        }
        String t = jsonLine.trim();
        try {
            JsonNode root = mapper.readTree(t);
            return root != null && root.isObject() && isCozeDebugMetadataObject(mapper, root);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isCozeDebugMetadataObject(ObjectMapper mapper, JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        boolean hasMarker = root.has("debug_url") || root.has("node_execute_uuid");
        if (!hasMarker) {
            return false;
        }
        if (nonBlank(text(root, "output")) || nonBlank(text(root, "answer"))) {
            return false;
        }
        JsonNode data = root.get("data");
        if (data != null && data.isObject()) {
            if (nonBlank(text(data, "output"))
                    || nonBlank(text(data, "answer"))
                    || nonBlank(text(data, "content"))) {
                return false;
            }
        }
        String content = text(root, "content");
        if (nonBlank(content)) {
            try {
                JsonNode inner = mapper.readTree(content);
                if (inner != null && inner.isObject() && !isCozeDebugMetadataObject(mapper, inner)) {
                    return false;
                }
            } catch (Exception ignored) {
                if (content.length() > 80) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String extractEndNodeContent(ObjectMapper mapper, String raw) {
        JsonNode endNode = tryParseEndNode(mapper, raw);
        if (endNode != null && "End".equalsIgnoreCase(text(endNode, "node_type"))) {
            return normalizeNodeContent(mapper, text(endNode, "content"));
        }
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.regionMatches(true, 0, "data:", 0, 5)) {
                continue;
            }
            String value = t.replaceFirst("(?i)^data:\\s*", "").trim();
            if (value.isBlank() || "[DONE]".equalsIgnoreCase(value)) {
                continue;
            }
            endNode = tryParseEndNode(mapper, value);
            if (endNode != null && "End".equalsIgnoreCase(text(endNode, "node_type"))) {
                return normalizeNodeContent(mapper, text(endNode, "content"));
            }
        }
        return null;
    }

    static String normalizeNodeContent(ObjectMapper mapper, String content) {
        String text = content == null ? null : content.trim();
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            JsonNode wrapped = mapper.readTree(text);
            if (wrapped != null && wrapped.isObject()) {
                String output = text(wrapped, "output");
                if (output != null && !output.isBlank()) {
                    return output;
                }
                String answer = text(wrapped, "answer");
                if (answer != null && !answer.isBlank()) {
                    return answer;
                }
                if (isCozeDebugMetadataObject(mapper, wrapped)) {
                    return "";
                }
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    static JsonNode tryParseEndNode(ObjectMapper mapper, String text) {
        try {
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isObject()) {
                return null;
            }
            if (root.has("node_type")) {
                return root;
            }
            JsonNode data = root.get("data");
            if (data != null && data.isObject() && data.has("node_type")) {
                return data;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractLastDataJson(String raw) {
        String last = null;
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (t.regionMatches(true, 0, "data:", 0, 5)) {
                String value = t.replaceFirst("(?i)^data:\\s*", "").trim();
                if (!value.isBlank() && !"[DONE]".equalsIgnoreCase(value)) {
                    last = value;
                }
            }
        }
        return last;
    }

    private static String tryExtractOutputFromJson(ObjectMapper mapper, String text) {
        try {
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isObject()) {
                return null;
            }
            String output =
                    firstNonBlank(
                            text(root, "output"),
                            text(root, "answer"),
                            text(root, "content"),
                            text(root, "message"));
            if (output != null && !output.isBlank()) {
                return output;
            }
            JsonNode data = root.get("data");
            if (data != null && data.isObject()) {
                output =
                        firstNonBlank(
                                text(data, "output"),
                                text(data, "answer"),
                                text(data, "content"),
                                text(data, "message"));
                if (output != null && !output.isBlank()) {
                    return output;
                }
                JsonNode result = data.get("result");
                if (result != null && !result.isNull()) {
                    return result.asText();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        return v.isTextual() ? v.asText() : v.toString();
    }
}
