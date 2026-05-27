package com.aaron.cloud.chat.websearch.cache;

import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** 从 {@code chat_message.meta_json#webSearchReferences} 解析引用。 */
public final class WebSearchGroundingMetaSupport {

    private WebSearchGroundingMetaSupport() {}

    public static List<WebSearchReference> parseReferencesFromMeta(ObjectMapper objectMapper, String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            if (!root.has("webSearchReferences") || !root.get("webSearchReferences").isArray()) {
                return List.of();
            }
            List<WebSearchReference> out = new ArrayList<>();
            for (JsonNode c : root.get("webSearchReferences")) {
                if (c == null || !c.isObject()) {
                    continue;
                }
                String summary = c.path("summary").asText("");
                if (summary.isEmpty()) {
                    summary = c.path("snippet").asText("");
                }
                out.add(
                        new WebSearchReference(
                                textOrEmpty(c, "title"),
                                textOrEmpty(c, "url"),
                                summary,
                                textOrNull(c, "siteName"),
                                textOrNull(c, "logoUrl"),
                                textOrNull(c, "publishTime"),
                                textOrNull(c, "extraJson"),
                                textOrNull(c, "sourceKey")));
            }
            return List.copyOf(out);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String textOrEmpty(JsonNode c, String field) {
        String t = c.path(field).asText("");
        return t == null ? "" : t;
    }

    private static String textOrNull(JsonNode c, String field) {
        if (!c.has(field) || c.get(field).isNull()) {
            return null;
        }
        String t = c.get(field).asText(null);
        return t == null || t.isBlank() ? null : t;
    }
}
