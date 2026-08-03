package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.scheduled.ScheduledTaskIntervalPreset;
import com.aaron.cloud.common.rag.entity.RagWebCrawlSite;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** 站点爬取是否到期（自然日间隔 + 当日时刻），对齐 ly-ai WebCrawlJobHandler.shouldUpdate 语义。 */
public final class RagWebCrawlSiteDueSupport {

    private RagWebCrawlSiteDueSupport() {}

    public static boolean isDue(RagWebCrawlSite site, LocalDateTime now) {
        ScheduledTaskIntervalPreset preset = site.getSchedulePreset();
        if (preset == null || preset.getIntervalDays() <= 0) {
            return false;
        }
        LocalTime runAt = site.getRunAtTime();
        if (runAt != null && now.toLocalTime().isBefore(runAt)) {
            return false;
        }
        LocalDateTime last = site.getLastCrawlAt();
        if (last == null) {
            return true;
        }
        long daysSinceLast = ChronoUnit.DAYS.between(last.toLocalDate(), now.toLocalDate());
        return daysSinceLast >= preset.getIntervalDays();
    }
}
