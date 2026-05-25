package com.aaron.cloud.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 中国时区（Asia/Shanghai）；JDBC 建议 {@code connectionTimeZone=%2B08%3A00}（+08:00）与库会话一致且不要求 MySQL 导入命名时区表。 */
public final class BeijingTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BeijingTime() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime nowLocal() {
        return LocalDateTime.now(ZONE);
    }

    public static String nowDisplayString() {
        return nowLocal().format(FMT);
    }

    public static String formatDisplay(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }
}
