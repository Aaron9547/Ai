package com.aaron.cloud.common.api.mcp.reminder;

import com.aaron.cloud.common.api.dto.IntentHandlerConfigOption;
import com.aaron.cloud.common.api.enums.chat.ReminderParseToolKind;
import java.util.List;

/** {@link ReminderParseToolKind} 与 MCP 限定名映射（管理端与编排共用）。 */
public final class ReminderParseToolCatalog {

    private ReminderParseToolCatalog() {}

    public static ReminderParseToolKind defaultKind() {
        return ReminderParseToolKind.RULE;
    }

    public static ReminderParseToolKind resolve(String code) {
        ReminderParseToolKind k = ReminderParseToolKind.fromCode(code);
        return k != null ? k : defaultKind();
    }

    public static String qualifiedName(ReminderParseToolKind kind) {
        if (kind == null) {
            return ReminderParseToolKind.ParseReminderBuiltinTools.RULE;
        }
        return kind.getQualifiedToolName();
    }

    public static List<IntentHandlerConfigOption> configOptions() {
        return List.of(
                new IntentHandlerConfigOption(
                        ReminderParseToolKind.RULE.getCode(),
                        ReminderParseToolKind.RULE.getAdminLabelZh()),
                new IntentHandlerConfigOption(
                        ReminderParseToolKind.LLM.getCode(),
                        ReminderParseToolKind.LLM.getAdminLabelZh()),
                new IntentHandlerConfigOption(
                        ReminderParseToolKind.HYBRID.getCode(),
                        ReminderParseToolKind.HYBRID.getAdminLabelZh()));
    }
}
