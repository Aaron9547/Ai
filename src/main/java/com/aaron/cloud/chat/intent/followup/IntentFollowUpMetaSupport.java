package com.aaron.cloud.chat.intent.followup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/** 助手消息 meta {@code intentFollowUpPrompts} 读写（与 SSE {@code followUpPrompts} items 结构一致）。 */
@UtilityClass
public final class IntentFollowUpMetaSupport {

    public static final String META_INTENT_FOLLOW_UP_PROMPTS = "intentFollowUpPrompts";

    public static void writePrompts(ObjectNode meta, List<String> promptTexts) {
        if (meta == null || promptTexts == null || promptTexts.isEmpty()) {
            return;
        }
        ArrayNode arr = meta.putArray(META_INTENT_FOLLOW_UP_PROMPTS);
        int i = 0;
        for (String text : promptTexts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            ObjectNode row = arr.addObject();
            row.put("id", (long) -(++i));
            row.put("text", text.strip());
            row.put("source", "INTENT_BUILTIN");
        }
        if (arr.isEmpty()) {
            meta.remove(META_INTENT_FOLLOW_UP_PROMPTS);
        }
    }

    public static List<IntentFollowUpPromptItem> parseFromRoot(JsonNode root) {
        if (root == null || !root.has(META_INTENT_FOLLOW_UP_PROMPTS) || !root.get(META_INTENT_FOLLOW_UP_PROMPTS).isArray()) {
            return List.of();
        }
        List<IntentFollowUpPromptItem> out = new ArrayList<>();
        for (JsonNode row : root.get(META_INTENT_FOLLOW_UP_PROMPTS)) {
            if (row == null || !row.isObject()) {
                continue;
            }
            String text =
                    row.has("text") && row.get("text").isTextual() ? row.get("text").asText().strip() : "";
            if (text.isEmpty()) {
                continue;
            }
            Long id = row.has("id") && row.get("id").canConvertToLong() ? row.get("id").asLong() : null;
            String source =
                    row.has("source") && row.get("source").isTextual() ? row.get("source").asText() : "INTENT_BUILTIN";
            out.add(new IntentFollowUpPromptItem(id, text, source));
        }
        return List.copyOf(out);
    }

    public record IntentFollowUpPromptItem(Long id, String text, String source) {}

    /** 将追问 chip 合并进已有助手 meta JSON 字符串（解析失败时返回原串）。 */
    public static String mergeIntoMetaJson(
            com.fasterxml.jackson.databind.ObjectMapper mapper, String existingMetaJson, List<String> promptTexts) {
        if (promptTexts == null || promptTexts.isEmpty()) {
            return existingMetaJson;
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode n =
                    existingMetaJson == null || existingMetaJson.isBlank()
                            ? mapper.createObjectNode()
                            : (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(existingMetaJson);
            writePrompts(n, promptTexts);
            return mapper.writeValueAsString(n);
        } catch (Exception ignored) {
            return existingMetaJson;
        }
    }
}
