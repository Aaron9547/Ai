package com.aaron.cloud.common.api.mcp.reminder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReminderCronSupportTest {

    @Test
    void normalize_replacesQuartzQuestionMark() {
        assertEquals("0 0 8 * * *", ReminderCronSupport.normalizeSpringCron("0 0 8 * * ?"));
    }

    @Test
    void assertValid_acceptsDailySpringCron() {
        assertDoesNotThrow(() -> ReminderCronSupport.assertValidSpringCron("0 0 9 * * *"));
    }
}
