package com.aaron.cloud.mcp.builtin.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ActiveReminderRef;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderParseServiceTest {

    private final ReminderParseService service = new ReminderParseService();

    @Test
    void createDaily() {
        ReminderParseResponse r =
                service.parse(
                        new ReminderParseRequest(
                                1,
                                "CREATE",
                                "提醒我每天早上8点检查日报",
                                1L,
                                1L,
                                "zh-CN",
                                0,
                                null));
        assertEquals("CREATE", r.op());
        assertNotNull(r.cronExpression());
        assertEquals("DAILY", r.scheduleType());
    }

    @Test
    void createDailyWithoutHour_defaultsToNine() {
        ReminderParseResponse r =
                service.parse(
                        new ReminderParseRequest(
                                1,
                                "CREATE",
                                "提醒我每天签到",
                                1L,
                                1L,
                                "zh-CN",
                                0,
                                null));
        assertEquals("CREATE", r.op());
        assertEquals("0 0 9 * * *", r.cronExpression());
        assertEquals("DAILY", r.scheduleType());
        assertEquals("签到", r.title());
    }

    @Test
    void cancelBare_listsChoices() {
        ReminderParseResponse r =
                service.parse(
                        new ReminderParseRequest(
                                1,
                                "CANCEL",
                                "取消提醒",
                                1L,
                                1L,
                                "zh-CN",
                                2,
                                List.of(
                                        new ActiveReminderRef(1L, "签到", "DAILY"),
                                        new ActiveReminderRef(2L, "日报", "DAILY"))));
        assertEquals("NOOP", r.op());
        assertEquals(
                "请选择要取消的提醒编号：1、签到；2、日报",
                r.userMessage());
    }
}
