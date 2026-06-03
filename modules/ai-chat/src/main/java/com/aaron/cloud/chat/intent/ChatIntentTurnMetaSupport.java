package com.aaron.cloud.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

/** 从消息 {@code meta_json} 判定本回合是否走意图处理器（用于跳过「猜你想问」等主链能力）。 */
@UtilityClass
public final class ChatIntentTurnMetaSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static boolean isIntentTurnMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(metaJson);
            if (root.path("intentHandled").asBoolean(false)) {
                return true;
            }
            if (root.path("intentRouted").asBoolean(false)) {
                return true;
            }
            if (root.has("intentFlowTicket")
                    && root.get("intentFlowTicket").isTextual()
                    && !root.get("intentFlowTicket").asText().isBlank()) {
                return true;
            }
            return root.has("activeReminderCount") && root.get("activeReminderCount").isNumber();
        } catch (Exception ignored) {
            return false;
        }
    }
}
