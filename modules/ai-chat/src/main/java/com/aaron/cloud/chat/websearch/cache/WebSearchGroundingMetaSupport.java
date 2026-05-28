package com.aaron.cloud.chat.websearch.cache;

import com.aaron.cloud.chat.websearch.WebSearchReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** 从 {@code chat_message.meta_json} 解析联网引用与复用问句。 */
public final class WebSearchGroundingMetaSupport {

    /** 与联网外呼一致的规范化问句（含附件拼接），用于会话内复用比对。 */
    public static final String META_WEB_SEARCH_QUERY_NORM = "webSearchQueryNorm";

    private WebSearchGroundingMetaSupport() {}

    public static String readQueryNormFromMeta(ObjectMapper objectMapper, String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(metaJson);
            if (!root.has(META_WEB_SEARCH_QUERY_NORM)) {
                return null;
            }
            String t = root.get(META_WEB_SEARCH_QUERY_NORM).asText(null);
            return t == null || t.isBlank() ? null : t.trim();
        } catch (Exception e) {
            return null;
        }
    }

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
