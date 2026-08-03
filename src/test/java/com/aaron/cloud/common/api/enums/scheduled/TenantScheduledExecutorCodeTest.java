package com.aaron.cloud.common.api.enums.scheduled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TenantScheduledExecutorCodeTest {

    @Test
    void byTaskCategory_splitsTenantAndChatReminder() {
        var tenant = TenantScheduledExecutorCode.byTaskCategory(TenantScheduledTaskCategory.TENANT_CRON);
        assertTrue(tenant.contains(TenantScheduledExecutorCode.RAG_WEB_CRAWL_DISPATCH));
        assertTrue(tenant.contains(TenantScheduledExecutorCode.CHAT_STARTER_DAILY_HOT));
        assertEquals(4, tenant.size());

        var chat =
                TenantScheduledExecutorCode.byTaskCategory(TenantScheduledTaskCategory.CHAT_USER_REMINDER);
        assertEquals(1, chat.size());
        assertEquals(TenantScheduledExecutorCode.CHAT_USER_REMINDER, chat.getFirst());
    }
}
