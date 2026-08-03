package com.aaron.cloud.chat.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatDailyRecommendJsonSupport {

    private static final Pattern JSON_ARRAY = Pattern.compile("\\[\\s*\\{.+]", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public static final String COLD_START_MODE = "COLD_START";

    public String toJson(List<DailyRecommendItemRecord> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("serialize daily recommend items failed", e);
        }
    }

    /** 无历史对话冷启动批次：落库带 mode 标记，便于有会话后升级为画像推荐。 */
    public String toJsonColdStart(List<DailyRecommendItemRecord> items) {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("mode", COLD_START_MODE);
            wrapper.put("items", items);
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new IllegalStateException("serialize cold-start daily recommend failed", e);
        }
    }

    public boolean isColdStartBatch(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(raw.trim());
            return node.has("mode")
                    && COLD_START_MODE.equalsIgnoreCase(node.get("mode").asText(""));
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<DailyRecommendItemRecord> parseItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            if (node.isArray()) {
                return extractFromArray(node);
            }
            if (node.has("list") && node.get("list").isArray()) {
                return extractFromArray(node.get("list"));
            }
            if (node.has("items") && node.get("items").isArray()) {
                return extractFromArray(node.get("items"));
            }
        } catch (Exception ignored) {
            // fall through
        }
        Matcher m = JSON_ARRAY.matcher(trimmed);
        if (m.find()) {
            try {
                return objectMapper.readValue(m.group(), new TypeReference<List<DailyRecommendItemRecord>>() {});
            } catch (Exception ignored) {
                // fall through
            }
        }
        return List.of();
    }

    private List<DailyRecommendItemRecord> extractFromArray(JsonNode arr) {
        List<DailyRecommendItemRecord> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (!item.isObject()) {
                continue;
            }
            String title = text(item, "title");
            if (title.isBlank()) {
                continue;
            }
            out.add(
                    new DailyRecommendItemRecord(
                            text(item, "tag"),
                            title,
                            text(item, "summary"),
                            text(item, "source"),
                            text(item, "date"),
                            text(item, "url")));
        }
        return out;
    }

    private static String text(JsonNode item, String field) {
        JsonNode n = item.get(field);
        if (n == null || n.isNull()) {
            return "";
        }
        return n.asText("").trim();
    }

    public record DailyRecommendItemRecord(
            String tag, String title, String summary, String source, String date, String url) {}
}
