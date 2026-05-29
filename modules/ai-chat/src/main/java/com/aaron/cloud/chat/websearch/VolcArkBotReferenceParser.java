package com.aaron.cloud.chat.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 解析火山 Ark Bot 响应中的联网引用（根 {@code references}、message 内引用、{@code bot_usage} 检索结果）。 */
final class VolcArkBotReferenceParser {

    private VolcArkBotReferenceParser() {}

    static List<WebSearchReference> mergeReferences(ObjectMapper objectMapper, JsonNode root) {
        Map<String, WebSearchReference> byKey = new LinkedHashMap<>();
        if (root == null || root.isMissingNode()) {
            return List.of();
        }
        if (root.path("references").isArray()) {
            for (JsonNode n : root.path("references")) {
                putDedupe(byKey, mapReferenceNode(objectMapper, n));
            }
        }
        JsonNode botChatRefs = root.path("bot_chat_result_reference");
        if (botChatRefs.isArray()) {
            for (JsonNode n : botChatRefs) {
                putDedupe(byKey, mapReferenceNode(objectMapper, n));
            }
        }
        JsonNode msgRefs = root.path("choices").path(0).path("message").path("references");
        if (msgRefs.isArray()) {
            for (JsonNode n : msgRefs) {
                putDedupe(byKey, mapReferenceNode(objectMapper, n));
            }
        }
        for (WebSearchReference r : parseBotUsageReferences(objectMapper, root)) {
            putDedupe(byKey, r);
        }
        return List.copyOf(byKey.values());
    }

    static String extractAssistantText(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "";
        }
        JsonNode message = root.path("choices").path(0).path("message");
        String content = textFromContentNode(message.path("content"));
        if (!content.isBlank()) {
            return content.trim();
        }
        return extractDeltaText(root);
    }

    static String extractDeltaText(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "";
        }
        return textFromContentNode(root.path("choices").path(0).path("delta").path("content")).trim();
    }

    static String summarizeReferenceDiagnostics(ObjectMapper objectMapper, JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "响应为空";
        }
        int rootRefs = root.path("references").isArray() ? root.path("references").size() : 0;
        int botChatRefs =
                root.path("bot_chat_result_reference").isArray()
                        ? root.path("bot_chat_result_reference").size()
                        : 0;
        int msgRefs =
                root.path("choices").path(0).path("message").path("references").isArray()
                        ? root.path("choices").path(0).path("message").path("references").size()
                        : 0;
        int botUsageRefs = parseBotUsageReferences(objectMapper, root).size();
        int searchCount = 0;
        JsonNode actionUsage = root.path("bot_usage").path("action_usage");
        if (actionUsage.isArray()) {
            for (JsonNode item : actionUsage) {
                if (item.path("search_count").isInt()) {
                    searchCount += item.path("search_count").asInt(0);
                }
            }
        }
        boolean hasActionDetails = root.path("bot_usage").path("action_details").isArray();
        return "root.references="
                + rootRefs
                + ", bot_chat_result_reference="
                + botChatRefs
                + ", message.references="
                + msgRefs
                + ", bot_usage.results="
                + botUsageRefs
                + ", action_usage.search_count="
                + searchCount
                + ", action_details="
                + (hasActionDetails ? "有" : "无");
    }

    private static void putDedupe(Map<String, WebSearchReference> byKey, WebSearchReference r) {
        String k = dedupeKey(r);
        if (!k.isBlank()) {
            byKey.putIfAbsent(k, r);
        }
    }

    private static String dedupeKey(WebSearchReference r) {
        if (r == null) {
            return "";
        }
        if (r.url() != null && !r.url().isBlank()) {
            return "u:" + r.url().trim();
        }
        if (r.title() != null && !r.title().isBlank()) {
            return "t:" + r.title().trim();
        }
        if (r.snippet() != null && !r.snippet().isBlank()) {
            String snip = r.snippet().trim();
            return "s:" + snip.substring(0, Math.min(120, snip.length()));
        }
        if (r.extraJson() != null && !r.extraJson().isBlank()) {
            return "x:" + r.extraJson().hashCode();
        }
        return "";
    }

    private static WebSearchReference mapReferenceNode(ObjectMapper objectMapper, JsonNode r) {
        if (r == null || !r.isObject()) {
            return WebSearchReference.ofTitleUrlSnippet("", "", "");
        }
        String title = firstNonBlank(textOrEmpty(r, "title"), textOrEmpty(r, "doc_title"), textOrEmpty(r, "chunk_title"));
        String u = firstNonBlank(textOrEmpty(r, "url"), textOrEmpty(r, "mobile_url"));
        String snippet = firstNonBlank(textOrEmpty(r, "summary"), textOrEmpty(r, "content"));
        String siteName = firstNonBlank(textOrEmpty(r, "site_name"), textOrEmpty(r, "siteName"));
        String logoUrl = firstNonBlank(textOrEmpty(r, "logo_url"), textOrEmpty(r, "logoUrl"));
        String publishTime = formatPublishTime(r.get("publish_time"));
        String extraJson = buildExtraJson(objectMapper, r.path("extra"), null);
        return new WebSearchReference(
                nz(title),
                nz(u),
                nz(snippet),
                blankToNull(siteName),
                blankToNull(logoUrl),
                blankToNull(publishTime),
                extraJson,
                null);
    }

    private static JsonNode firstResultsArray(JsonNode output) {
        if (output == null || output.isMissingNode()) {
            return output;
        }
        JsonNode[] candidates = {
            output.path("data").path("data").path("results"),
            output.path("data").path("results"),
            output.path("results"),
            output.path("documents"),
            output.path("data").path("documents")
        };
        for (JsonNode c : candidates) {
            if (c.isArray() && !c.isEmpty()) {
                return c;
            }
        }
        for (JsonNode c : candidates) {
            if (c.isArray()) {
                return c;
            }
        }
        return output.path("data").path("data").path("results");
    }

    private static List<WebSearchReference> parseBotUsageReferences(ObjectMapper objectMapper, JsonNode root) {
        List<WebSearchReference> list = new ArrayList<>();
        JsonNode details = root.path("bot_usage").path("action_details");
        if (!details.isArray()) {
            return list;
        }
        for (JsonNode ad : details) {
            JsonNode tools = ad.path("tool_details");
            if (!tools.isArray()) {
                continue;
            }
            for (JsonNode td : tools) {
                JsonNode output = resolveOutputNode(objectMapper, td.path("output"));
                JsonNode results = firstResultsArray(output);
                if (!results.isArray()) {
                    continue;
                }
                for (JsonNode item : results) {
                    list.add(mapSearchResultItem(objectMapper, item));
                }
            }
        }
        return list;
    }

    private static JsonNode resolveOutputNode(ObjectMapper objectMapper, JsonNode output) {
        if (output == null || output.isMissingNode() || output.isNull()) {
            return output;
        }
        if (output.isTextual()) {
            String text = output.asText("").trim();
            if (text.isEmpty()) {
                return output;
            }
            try {
                return objectMapper.readTree(text);
            } catch (Exception ignored) {
                return output;
            }
        }
        return output;
    }

    private static WebSearchReference mapSearchResultItem(ObjectMapper objectMapper, JsonNode r) {
        if (r == null || !r.isObject()) {
            return WebSearchReference.ofTitleUrlSnippet("", "", "");
        }
        String title = textOrEmpty(r, "title");
        String u = firstNonBlank(textOrEmpty(r, "url"), textOrEmpty(r, "mobile_url"));
        String snippet = firstNonBlank(textOrEmpty(r, "summary"), textOrEmpty(r, "content"));
        String siteName = firstNonBlank(textOrEmpty(r, "site_name"), textOrEmpty(r, "siteName"));
        JsonNode spd = r.path("search_plugin_data");
        String logoUrl = firstNonBlank(textOrEmpty(r, "logo_url"), textOrEmpty(r, "logoUrl"));
        if ((logoUrl == null || logoUrl.isBlank()) && spd.isObject()) {
            logoUrl = textOrEmpty(spd, "logo_url");
        }
        String publishTime = formatPublishTime(r.get("publish_time"));
        String extraJson = buildExtraJson(objectMapper, r.path("extra"), spd.isObject() ? spd : null);
        return new WebSearchReference(
                nz(title),
                nz(u),
                nz(snippet),
                blankToNull(siteName),
                blankToNull(logoUrl),
                blankToNull(publishTime),
                extraJson,
                null);
    }

    private static String buildExtraJson(ObjectMapper objectMapper, JsonNode extra, JsonNode pluginData) {
        boolean hasE = extra != null && !extra.isMissingNode() && !extra.isNull();
        boolean hasP = pluginData != null && !pluginData.isMissingNode() && !pluginData.isNull();
        if (!hasE && !hasP) {
            return null;
        }
        try {
            if (hasE && !hasP) {
                return objectMapper.writeValueAsString(extra);
            }
            if (!hasE) {
                return objectMapper.writeValueAsString(pluginData);
            }
            ObjectNode wrap = objectMapper.createObjectNode();
            wrap.set("extra", extra);
            wrap.set("search_plugin_data", pluginData);
            return objectMapper.writeValueAsString(wrap);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatPublishTime(JsonNode pt) {
        if (pt == null || pt.isNull() || pt.isMissingNode()) {
            return null;
        }
        if (pt.isIntegralNumber()) {
            long epoch = pt.asLong();
            if (epoch <= 0) {
                return null;
            }
            return Long.toString(epoch);
        }
        if (pt.isTextual()) {
            String t = pt.asText().trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static String textFromContentNode(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part == null || part.isNull()) {
                    continue;
                }
                if (part.isTextual()) {
                    sb.append(part.asText(""));
                    continue;
                }
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return content.asText("");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

    private static String textOrEmpty(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() ? v.asText().trim() : "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
