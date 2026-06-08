package com.aaron.cloud.chat.intent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ReminderCancelSelectionGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsePending_trueWhenFlagSet() {
        String meta =
                """
                {"intentHandled":true,"reminderCancelSelectionPending":true}
                """;
        assertTrue(ReminderCancelSelectionGate.parsePending(meta, MAPPER));
    }

    @Test
    void parsePending_falseWhenMissingOrNoop() {
        assertFalse(ReminderCancelSelectionGate.parsePending(null, MAPPER));
        assertFalse(ReminderCancelSelectionGate.parsePending("{}", MAPPER));
        assertFalse(
                ReminderCancelSelectionGate.parsePending(
                        """
                        {"reminderCancelSelectionPending":false}
                        """,
                        MAPPER));
    }
}
