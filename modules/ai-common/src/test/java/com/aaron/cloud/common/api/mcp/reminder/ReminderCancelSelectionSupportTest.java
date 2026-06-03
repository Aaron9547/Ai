package com.aaron.cloud.common.api.mcp.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aaron.cloud.common.api.mcp.reminder.ReminderCancelSelectionSupport.IndexParseKind;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ActiveReminderRef;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderCancelSelectionSupportTest {

    private static final List<ActiveReminderRef> TWO =
            List.of(
                    new ActiveReminderRef(101L, "签到，淘金币、签到币", "DAILY"),
                    new ActiveReminderRef(102L, "检查日报", "DAILY"));

    @Test
    void bareCancel_listsNumberedChoices() {
        ReminderParseResponse r = ReminderCancelSelectionSupport.resolveCancel("取消提醒", TWO);
        assertEquals("NOOP", r.op());
        assertNull(r.error());
        assertEquals(
                "请选择要取消的提醒编号：1、签到，淘金币、签到币；2、检查日报",
                r.userMessage());
    }

    @Test
    void indexOne_cancelsFirst() {
        ReminderParseResponse r = ReminderCancelSelectionSupport.resolveCancel("1", TWO);
        assertEquals("CANCEL", r.op());
        assertEquals(101L, r.cancelReminderId());
        assertEquals(List.of(101L), r.cancelReminderIds());
    }

    @Test
    void indexOneAndTwo_cancelsBoth() {
        ReminderParseResponse r = ReminderCancelSelectionSupport.resolveCancel("1、2", TWO);
        assertEquals("CANCEL", r.op());
        assertEquals(List.of(101L, 102L), r.cancelReminderIds());
    }

    @Test
    void indexTwelve_invalid() {
        ReminderParseResponse r = ReminderCancelSelectionSupport.resolveCancel("12", TWO);
        assertNull(r.op());
        assertEquals(ReminderCancelSelectionSupport.INVALID_INDEX_USER_MESSAGE, r.error());
    }

    @Test
    void parseIndices_commaSeparated() {
        var o = ReminderCancelSelectionSupport.parseListIndices("1,2", 2);
        assertEquals(IndexParseKind.VALID, o.kind());
        assertEquals(List.of(1, 2), o.indices());
    }
}
