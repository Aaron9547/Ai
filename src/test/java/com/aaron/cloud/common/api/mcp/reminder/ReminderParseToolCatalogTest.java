package com.aaron.cloud.common.api.mcp.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aaron.cloud.common.api.enums.chat.ReminderParseToolKind;
import org.junit.jupiter.api.Test;

class ReminderParseToolCatalogTest {

    @Test
    void qualifiedName_mapsAllKinds() {
        assertEquals(
                ReminderParseToolKind.ParseReminderBuiltinTools.RULE,
                ReminderParseToolCatalog.qualifiedName(ReminderParseToolKind.RULE));
        assertEquals(
                ReminderParseToolKind.ParseReminderBuiltinTools.LLM,
                ReminderParseToolCatalog.qualifiedName(ReminderParseToolKind.LLM));
        assertEquals(
                ReminderParseToolKind.ParseReminderBuiltinTools.HYBRID,
                ReminderParseToolCatalog.qualifiedName(ReminderParseToolKind.HYBRID));
    }

    @Test
    void fromQualified_legacyRule() {
        assertEquals(
                ReminderParseToolKind.RULE,
                ReminderParseToolKind.fromQualifiedToolName("platform::parse_reminder"));
    }
}
