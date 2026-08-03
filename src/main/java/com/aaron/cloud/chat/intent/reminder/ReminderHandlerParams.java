package com.aaron.cloud.chat.intent.reminder;

import com.aaron.cloud.common.api.enums.chat.ReminderParseToolKind;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseToolCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ReminderHandlerParams(
        ReminderParseToolKind parseToolKind,
        String parseModelAlias,
        int defaultMaxDays,
        int maxActiveReminders) {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderHandlerParams.class);

    public static ReminderHandlerParams defaults() {
        return new ReminderHandlerParams(ReminderParseToolCatalog.defaultKind(), null, 90, 20);
    }

    /** 编排层调用 MCP 时使用的限定名（不暴露给管理端表单）。 */
    public String mcpQualifiedToolName() {
        return ReminderParseToolCatalog.qualifiedName(parseToolKind);
    }

    public static ReminderHandlerParams parse(String extraConfigJson, ObjectMapper objectMapper) {
        ReminderHandlerParams d = defaults();
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return d;
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            JsonNode hp = root.path("handlerParams");
            if (!hp.isObject()) {
                return d;
            }
            ReminderParseToolKind kind = resolveKind(hp, d.parseToolKind());
            String modelAlias = text(hp, "parseModelAlias");
            int maxDays = intVal(hp, "defaultMaxDays", d.defaultMaxDays());
            int maxActive = intVal(hp, "maxActiveReminders", d.maxActiveReminders());
            return new ReminderHandlerParams(kind, modelAlias, maxDays, maxActive);
        } catch (Exception e) {
            LOG.warn("ReminderHandlerParams parse failed: {}", e.toString());
            return d;
        }
    }

    private static ReminderParseToolKind resolveKind(JsonNode hp, ReminderParseToolKind fallback) {
        String kindCode = text(hp, "parseToolKind");
        if (kindCode != null) {
            return ReminderParseToolCatalog.resolve(kindCode);
        }
        String legacyTool = text(hp, "mcpQualifiedToolName");
        if (legacyTool != null) {
            ReminderParseToolKind fromQ = ReminderParseToolKind.fromQualifiedToolName(legacyTool);
            if (fromQ != null) {
                return fromQ;
            }
        }
        return fallback;
    }

    private static String text(JsonNode parent, String field) {
        if (!parent.has(field) || parent.get(field).isNull()) {
            return null;
        }
        String t = parent.get(field).asText("").strip();
        return t.isEmpty() ? null : t;
    }

    private static int intVal(JsonNode parent, String field, int fallback) {
        if (!parent.has(field) || !parent.get(field).canConvertToInt()) {
            return fallback;
        }
        return parent.get(field).asInt(fallback);
    }
}
