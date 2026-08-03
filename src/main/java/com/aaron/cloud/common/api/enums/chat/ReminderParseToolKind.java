package com.aaron.cloud.common.api.enums.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一句话办事：话术解析方式（管理端 {@code parseToolKind}，映射内置 MCP 工具）。 */
@Getter
@RequiredArgsConstructor
public enum ReminderParseToolKind {
    RULE("RULE", "规则解析（快速、不耗模型）", ParseReminderBuiltinTools.RULE),
    LLM("LLM", "智能解析（大模型）", ParseReminderBuiltinTools.LLM),
    HYBRID("HYBRID", "规则优先，失败时智能解析", ParseReminderBuiltinTools.HYBRID);

    private final String code;
    private final String adminLabelZh;
    private final String qualifiedToolName;

    public static ReminderParseToolKind fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().toUpperCase();
        for (ReminderParseToolKind k : values()) {
            if (k.code.equals(n)) {
                return k;
            }
        }
        return null;
    }

    public static ReminderParseToolKind fromQualifiedToolName(String qualified) {
        if (qualified == null || qualified.isBlank()) {
            return null;
        }
        String q = qualified.trim();
        for (ReminderParseToolKind k : values()) {
            if (k.qualifiedToolName.equals(q)) {
                return k;
            }
        }
        if (ParseReminderBuiltinTools.RULE.equals(q)) {
            return RULE;
        }
        return null;
    }

    /** 内置工具限定名常量（与 ai-mcp / ai-chat BuiltinMcpTool 一致）。 */
    public static final class ParseReminderBuiltinTools {
        public static final String RULE = "platform::parse_reminder";
        public static final String LLM = "platform::parse_reminder_llm";
        public static final String HYBRID = "platform::parse_reminder_hybrid";

        private ParseReminderBuiltinTools() {}
    }
}
