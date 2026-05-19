package com.aaron.cloud.scheduled;

import com.aaron.cloud.common.scheduled.entity.TenantScheduledTask;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

/** 基于 cron 的调度到期判断（对齐电表 BaseAbstractJob 周期防重思路）。 */
public final class TenantScheduledCronSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private TenantScheduledCronSupport() {}

    public static boolean isDue(TenantScheduledTask task, LocalDateTime now) {
        if (task.getEnabled() == null || task.getEnabled() != 1) {
            return false;
        }
        String cron = task.getCronExpression();
        if (cron == null || cron.isBlank()) {
            return false;
        }
        CronExpression expression = CronExpression.parse(cron.trim());
        LocalDateTime last = task.getLastExecAt();
        if (last == null) {
            return true;
        }
        ZonedDateTime lastZ = last.atZone(ZONE);
        ZonedDateTime next = expression.next(lastZ);
        if (next == null) {
            return false;
        }
        return !now.atZone(ZONE).isBefore(next);
    }

    public static LocalDateTime computeNextExecAt(TenantScheduledTask task, LocalDateTime after) {
        String cron = task.getCronExpression();
        if (cron == null || cron.isBlank()) {
            return null;
        }
        CronExpression expression = CronExpression.parse(cron.trim());
        ZonedDateTime base = (after != null ? after : LocalDateTime.now()).atZone(ZONE);
        ZonedDateTime next = expression.next(base);
        return next != null ? next.toLocalDateTime() : null;
    }
}
