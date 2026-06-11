package com.aaron.cloud.common.api.mcp.reminder;

import org.springframework.scheduling.support.CronExpression;

/** 一句话提醒：Spring 6 段 cron 归一化与校验。 */
public final class ReminderCronSupport {

    private ReminderCronSupport() {}

    /**
     * 将模型或外部工具可能返回的 Quartz 风格 {@code ?} 转为 Spring {@code *}，并压缩空白。
     */
    public static String normalizeSpringCron(String cron) {
        if (cron == null) {
            return null;
        }
        return cron.trim().replace('?', '*').replaceAll("\\s+", " ");
    }

    public static void assertValidSpringCron(String cron) {
        String normalized = normalizeSpringCron(cron);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("empty cron");
        }
        CronExpression.parse(normalized);
    }
}
