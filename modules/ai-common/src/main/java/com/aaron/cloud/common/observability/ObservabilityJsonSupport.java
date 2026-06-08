package com.aaron.cloud.common.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 可观测性落库 JSON 截断。 */
public final class ObservabilityJsonSupport {

    public static final int DEFAULT_MAX_CHARS = 8192;

    private ObservabilityJsonSupport() {}

    public static String truncate(String raw, int maxChars) {
        if (raw == null) {
            return null;
        }
        if (raw.length() <= maxChars) {
            return raw;
        }
        return raw.substring(0, maxChars) + "…[truncated]";
    }

    public static String truncateJson(ObjectMapper mapper, JsonNode node, int maxChars) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return truncate(mapper.writeValueAsString(node), maxChars);
        } catch (Exception e) {
            return truncate(String.valueOf(node), maxChars);
        }
    }

    public static String truncateJson(ObjectMapper mapper, Object value, int maxChars) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(mapper.writeValueAsString(value), maxChars);
        } catch (Exception e) {
            return truncate(String.valueOf(value), maxChars);
        }
    }
}
