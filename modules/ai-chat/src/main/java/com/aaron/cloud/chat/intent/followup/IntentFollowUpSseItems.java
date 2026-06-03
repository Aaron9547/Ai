package com.aaron.cloud.chat.intent.followup;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import lombok.experimental.UtilityClass;

/** SSE {@code followUpPrompts} 帧 items 数组拼装。 */
@UtilityClass
public final class IntentFollowUpSseItems {

    public static void writeItems(ObjectNode root, List<String> texts) {
        ArrayNode items = root.putArray("items");
        int i = 0;
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            ObjectNode row = items.addObject();
            row.put("id", (long) -(++i));
            row.put("text", text.strip());
            row.put("source", "INTENT_BUILTIN");
        }
    }
}
