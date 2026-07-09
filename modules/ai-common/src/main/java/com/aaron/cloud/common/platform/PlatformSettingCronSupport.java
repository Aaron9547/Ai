package com.aaron.cloud.common.platform;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;

/** 平台参数 Cron 是否应在当前分钟触发（Asia/Shanghai）。 */
@Slf4j
final class PlatformSettingCronSupport {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private PlatformSettingCronSupport() {}

    static boolean isDueNow(String cronText) {
        if (cronText == null || cronText.isBlank()) {
            return false;
        }
        try {
            CronExpression expression = CronExpression.parse(cronText.trim());
            ZonedDateTime now = ZonedDateTime.now(ZONE).withSecond(0).withNano(0);
            ZonedDateTime from = now.minusMinutes(1);
            ZonedDateTime next = expression.next(from);
            return next != null && !next.isAfter(now) && next.isAfter(from);
        } catch (Exception ex) {
            log.warn("invalid platform cron expression: {}", cronText, ex);
            return false;
        }
    }
}
