package com.aaron.cloud.common.api.mcp.reminder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** {@code platform::parse_reminder} 与远程替代工具共用的 JSON 契约（contractVersion=1）。 */
public final class ReminderParseContracts {

    public static final int CONTRACT_VERSION = 1;

    private ReminderParseContracts() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReminderParseRequest(
            Integer contractVersion,
            String action,
            String utterance,
            Long userId,
            Long tenantId,
            String locale,
            Integer activeReminderCount,
            List<ActiveReminderRef> activeReminders) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActiveReminderRef(Long id, String title, String scheduleType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReminderParseResponse(
            Integer contractVersion,
            String op,
            String title,
            String actionText,
            String scheduleType,
            String cronExpression,
            String endsAt,
            Long cancelReminderId,
            /** 批量取消时的提醒 id 列表（列表序号 1..n 映射 {@code activeReminders} 顺序）。 */
            List<Long> cancelReminderIds,
            String cancelMatch,
            String userMessage,
            String error) {}
}
