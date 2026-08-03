package com.aaron.cloud.mcp.builtin.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aaron.cloud.mcp.builtin.reminder.ReminderScheduleExtractor.ScheduleExtracted;
import org.junit.jupiter.api.Test;

class ReminderScheduleExtractorTest {

    @Test
    void daily_chineseNineOClock() {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule("提醒我每天九点签到");
        assertNotNull(ex);
        assertEquals("DAILY", ex.type());
        assertEquals("0 0 9 * * *", ex.cron());
        assertEquals("签到", ReminderScheduleExtractor.extractActionText("提醒我每天九点签到"));
    }

    @Test
    void daily_chineseHalfPast() {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule("每晚九点半提醒我吃药");
        assertNotNull(ex);
        assertEquals("DAILY", ex.type());
        assertEquals("0 30 21 * * *", ex.cron()); // 每晚 → 晚间语境，九点半 = 21:30
    }

    @Test
    void daily_colonTime() {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule("每天08:30打卡");
        assertNotNull(ex);
        assertEquals("0 30 8 * * *", ex.cron());
    }

    @Test
    void daily_pmChinese() {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule("每天下午3点开会");
        assertNotNull(ex);
        assertEquals("0 0 15 * * *", ex.cron());
    }

    @Test
    void daily_implicitTimeOnly() {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule("提醒我九点签到");
        assertNotNull(ex);
        assertEquals("DAILY", ex.type());
        assertEquals("0 0 9 * * *", ex.cron());
    }

    @Test
    void weekly_chineseMonday() {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule("每周一九点站会");
        assertNotNull(ex);
        assertEquals("WEEKLY", ex.type());
        assertEquals("0 0 9 * * 1", ex.cron());
    }

    @Test
    void english_everyDayAtNineAm() {
        ScheduleExtracted ex =
                ReminderScheduleExtractor.extractSchedule("Remind me to check in every day at 9am");
        assertNotNull(ex);
        assertEquals("DAILY", ex.type());
        assertEquals("0 0 9 * * *", ex.cron());
        assertEquals(
                "check in",
                ReminderScheduleExtractor.extractActionText("Remind me to check in every day at 9am"));
    }

    @Test
    void english_everyMondayHalfPast() {
        ScheduleExtracted ex =
                ReminderScheduleExtractor.extractSchedule("Remind me every Monday at half past 8 to sync");
        assertNotNull(ex);
        assertEquals("WEEKLY", ex.type());
        assertEquals("0 30 8 * * 1", ex.cron());
    }

    @Test
    void english_tomorrowNine() {
        ScheduleExtracted ex =
                ReminderScheduleExtractor.extractSchedule("Remind me tomorrow at 9 to submit report");
        assertNotNull(ex);
        assertEquals("ONCE", ex.type());
        assertNotNull(ex.endsAt());
    }
}
