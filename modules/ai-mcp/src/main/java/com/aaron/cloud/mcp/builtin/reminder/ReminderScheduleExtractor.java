package com.aaron.cloud.mcp.builtin.reminder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一句话提醒：中英文多写法时间与周期抽取（规则解析真源）。
 *
 * <p>支持例如「每天九点」「每晚九点半」「8:30am」「every day at 9」「every Monday at 8am」等；
 * 仅给出时刻未写周期时，在提醒创建语境下默认按每天处理。
 */
public final class ReminderScheduleExtractor {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_DAILY_HOUR = 9;

    private static final Pattern CLOCK =
            Pattern.compile("(?<!\\d)(\\d{1,2})[:：](\\d{2})(?:\\s*(?:am|pm|a\\.m\\.|p\\.m\\.))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_AT_TIME =
            Pattern.compile(
                    "(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(?:o['']?clock)?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_HALF_PAST =
            Pattern.compile("half\\s+past\\s+(\\d{1,2}|noon|midnight)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARABIC_HOUR =
            Pattern.compile("(\\d{1,2})\\s*(?:点|时|点钟)(?:\\s*(\\d{1,2})\\s*分?|半)?");
    private static final Pattern CHINESE_HOUR =
            Pattern.compile(
                    "(二十(?:[一二三])?|十(?:[一二三四五六七八九])?|[零一二三四五六七八九])\\s*(?:点|时|点钟)(?:\\s*(\\d{1,2})\\s*分?|半)?");
    private static final Pattern ZH_WEEKLY = Pattern.compile("每(?:周|星期)([一二三四五六日天])");
    private static final Pattern EN_WEEKLY =
            Pattern.compile(
                    "(?:every|each|on)\\s+"
                            + "(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)"
                            + "(?:s)?\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_MONTHLY =
            Pattern.compile("每(?:月|个月)(?:的)?(\\d{1,2}|[一二三四五六七八九十廿卅]{1,3})\\s*(?:日|号)");
    private static final Pattern EN_MONTHLY =
            Pattern.compile(
                    "(?:every\\s+month\\s+on\\s+the|on\\s+the)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s+of\\s+each\\s+month)?",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ONCE_DATE =
            Pattern.compile("(\\d{4})[\\-/年](\\d{1,2})[\\-/月](\\d{1,2})");
    private static final Pattern EN_DAILY = Pattern.compile("\\b(?:every\\s+day|each\\s+day|daily)\\b", Pattern.CASE_INSENSITIVE);

    private ReminderScheduleExtractor() {}

    public record TimeOfDay(int hour, int minute) {}

    public record ScheduleExtracted(
            String type,
            String cron,
            String endsAt,
            int hour,
            int minute,
            int dow,
            int dayOfMonth,
            String dowLabel) {}

    public static boolean prefersEnglish(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en");
    }

    public static boolean containsCreateLexicon(String utterance) {
        String u = normalizeChars(utterance);
        String lower = u.toLowerCase(Locale.ROOT);
        return u.contains("提醒我")
                || u.contains("请提醒")
                || u.contains("帮我提醒")
                || u.contains("定时提醒")
                || u.contains("邮件提醒")
                || lower.contains("remind me")
                || lower.contains("set a reminder")
                || lower.contains("set reminder");
    }

    public static boolean containsCancelLexicon(String utterance) {
        String u = normalizeChars(utterance);
        String lower = u.toLowerCase(Locale.ROOT);
        return u.contains("取消提醒")
                || u.contains("关闭提醒")
                || u.contains("不要提醒")
                || u.contains("停止提醒")
                || u.contains("取消通知")
                || lower.contains("cancel reminder")
                || lower.contains("delete reminder")
                || lower.contains("stop reminder")
                || lower.contains("remove reminder");
    }

    public static ScheduleExtracted extractSchedule(String utterance) {
        String u = normalizeChars(utterance);
        if (u.isEmpty()) {
            return null;
        }
        String lower = u.toLowerCase(Locale.ROOT);

        Matcher zw = ZH_WEEKLY.matcher(u);
        if (zw.find()) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            int dow = chineseDow(zw.group(1));
            String cron = cronWeekly(tod, dow);
            return new ScheduleExtracted("WEEKLY", cron, null, tod.hour(), tod.minute(), dow, 0, zw.group(1));
        }
        Matcher ew = EN_WEEKLY.matcher(lower);
        if (ew.find()) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            int dow = englishDow(ew.group(1));
            String label = ew.group(1);
            String cron = cronWeekly(tod, dow);
            return new ScheduleExtracted("WEEKLY", cron, null, tod.hour(), tod.minute(), dow, 0, label);
        }

        Matcher zm = ZH_MONTHLY.matcher(u);
        if (zm.find()) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            int dom = parseDayOfMonthToken(zm.group(1));
            if (dom < 1 || dom > 31) {
                return null;
            }
            String cron = cronMonthly(tod, dom);
            return new ScheduleExtracted("MONTHLY", cron, null, tod.hour(), tod.minute(), 0, dom, null);
        }
        Matcher em = EN_MONTHLY.matcher(lower);
        if (em.find()) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            int dom = Integer.parseInt(em.group(1));
            String cron = cronMonthly(tod, dom);
            return new ScheduleExtracted("MONTHLY", cron, null, tod.hour(), tod.minute(), 0, dom, null);
        }

        if (isDaily(u, lower)) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                tod = new TimeOfDay(DEFAULT_DAILY_HOUR, 0);
            }
            String cron = cronDaily(tod);
            return new ScheduleExtracted("DAILY", cron, null, tod.hour(), tod.minute(), 0, 0, null);
        }

        Matcher od = ONCE_DATE.matcher(u);
        if (od.find()) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            int y = Integer.parseInt(od.group(1));
            int mo = Integer.parseInt(od.group(2));
            int da = Integer.parseInt(od.group(3));
            LocalDateTime at = LocalDateTime.of(y, mo, da, tod.hour(), tod.minute());
            if (at.isBefore(LocalDateTime.now(ZONE))) {
                return null;
            }
            String cron = cronOnce(tod, da, mo);
            return new ScheduleExtracted("ONCE", cron, at.toString(), tod.hour(), tod.minute(), 0, 0, null);
        }

        if (u.contains("明天") || u.contains("明日") || lower.contains("tomorrow")) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            LocalDate day = LocalDate.now(ZONE).plusDays(1);
            LocalDateTime at = LocalDateTime.of(day, LocalTime.of(tod.hour(), tod.minute()));
            String cron = cronOnce(tod, day.getDayOfMonth(), day.getMonthValue());
            return new ScheduleExtracted("ONCE", cron, at.toString(), tod.hour(), tod.minute(), 0, 0, null);
        }

        if (u.contains("一次") || u.contains("某天") || lower.contains(" one time") || lower.contains("once ")) {
            TimeOfDay tod = extractTime(u);
            if (tod == null) {
                return null;
            }
            LocalDateTime at =
                    LocalDateTime.now(ZONE).plusDays(1).withHour(tod.hour()).withMinute(tod.minute()).withSecond(0);
            LocalDate day = at.toLocalDate();
            String cron = cronOnce(tod, day.getDayOfMonth(), day.getMonthValue());
            return new ScheduleExtracted("ONCE", cron, at.toString(), tod.hour(), tod.minute(), 0, 0, null);
        }

        TimeOfDay implicit = extractTime(u);
        if (implicit != null) {
            String cron = cronDaily(implicit);
            return new ScheduleExtracted("DAILY", cron, null, implicit.hour(), implicit.minute(), 0, 0, null);
        }
        return null;
    }

    public static String extractActionText(String utterance) {
        String t = normalizeChars(utterance);
        String lower = t.toLowerCase(Locale.ROOT);
        t =
                t.replaceAll(
                        "(?i)remind\\s+me\\s+to\\s+|remind\\s+me\\s+that\\s+|remind\\s+me\\s+"
                                + "|set\\s+a\\s+reminder\\s+to\\s+|set\\s+reminder\\s+(?:for|to)\\s+"
                                + "|please\\s+remind\\s+me\\s+(?:to\\s+)?");
        t = t.replaceAll("提醒我|请提醒|帮我提醒|帮我|请|定时提醒|邮件提醒|提醒我一下", "");
        t = t.replaceAll("(?i)every\\s+day\\s+at|each\\s+day\\s+at|daily\\s+at|every\\s+morning\\s+at|every\\s+evening\\s+at", "");
        t =
                t.replaceAll(
                        "每天|每日|天天|成天|每晚|每早|每天早晨|每天早上|每天上午|每天下午|每天晚上|每个工作日",
                        "");
        t = t.replaceAll("(?i)every\\s+day|each\\s+day|\\bdaily\\b|every\\s+morning|every\\s+evening", "");
        t = t.replaceAll("(?i)every\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)s?", "");
        t = t.replaceAll("每(?:周|星期)[一二三四五六日天]", "");
        t = t.replaceAll("每(?:月|个月)(?:的)?\\d{1,2}\\s*(?:日|号)", "");
        t = t.replaceAll("明天|明日|(?i)tomorrow", "");
        t = CHINESE_HOUR.matcher(t).replaceAll("");
        t = t.replaceAll("\\d{1,2}\\s*(?:点|时|点钟)(?:\\s*\\d{1,2}\\s*分?|半)?", "");
        t = t.replaceAll("(?i)\\d{1,2}[:：]\\d{2}(?:\\s*(?:am|pm|a\\.m\\.|p\\.m\\.))?", "");
        t = t.replaceAll("(?i)(?:at\\s+)?\\d{1,2}(?:\\s*(?:o'clock|am|pm|a\\.m\\.|p\\.m\\.))?", "");
        t = t.replaceAll("(?i)half\\s+past\\s+(?:\\d{1,2}|noon|midnight)", "");
        t = t.replaceAll("上午|下午|早上|晚上|清晨|早晨|傍晚|午后|凌晨", "");
        t = t.replaceAll("(?i)\\b(?:am|pm|a\\.m\\.|p\\.m\\.)\\b", "");
        t = t.replaceAll("\\d{4}[\\-/年]\\d{1,2}[\\-/月]\\d{1,2}", "");
        t = t.replaceAll("一次|(?i)one\\s+time|(?i)\\bonce\\b", "");
        t = t.replaceAll("^[\\s,，、:：]+|[\\s,，、:：]+$", "");
        return t.strip();
    }

    public static String formatCreateUserMessage(String locale, ScheduleExtracted ex, String title) {
        boolean en = prefersEnglish(locale);
        String time = ex.hour() + ":" + String.format("%02d", ex.minute());
        return switch (ex.type) {
            case "DAILY" ->
                    en
                            ? "Daily email reminder set for " + time + ": " + title
                            : "已设置每天 " + time + " 邮件提醒：" + title;
            case "WEEKLY" -> {
                String dow = ex.dowLabel() == null ? "" : ex.dowLabel();
                yield en
                        ? "Weekly email reminder set for " + dow + " at " + time + ": " + title
                        : "已设置每周 " + dow + " " + time + " 邮件提醒：" + title;
            }
            case "MONTHLY" ->
                    en
                            ? "Monthly email reminder on day " + ex.dayOfMonth() + " at " + time + ": " + title
                            : "已设置每月 " + ex.dayOfMonth() + " 日 " + time + " 邮件提醒：" + title;
            default ->
                    en ? "Reminder set: " + title : "已设置提醒：" + title;
        };
    }

    static TimeOfDay extractTime(String utterance) {
        String u = normalizeChars(utterance);
        String lower = u.toLowerCase(Locale.ROOT);

        if (lower.contains("noon") || u.contains("正午") || u.contains("中午十二")) {
            return finalizeTime(u, lower, new TimeOfDay(12, 0));
        }
        if (lower.contains("midnight") || u.contains("午夜") || u.contains("半夜十二")) {
            return new TimeOfDay(0, 0);
        }

        Matcher half = EN_HALF_PAST.matcher(lower);
        if (half.find()) {
            String token = half.group(1);
            int h =
                    "noon".equalsIgnoreCase(token)
                            ? 12
                            : "midnight".equalsIgnoreCase(token) ? 0 : Integer.parseInt(token);
            return finalizeTime(u, lower, validTime(h, 30));
        }

        Matcher clock = CLOCK.matcher(u);
        if (clock.find()) {
            int h = Integer.parseInt(clock.group(1));
            int m = Integer.parseInt(clock.group(2));
            String suffix = clock.group(0).toLowerCase(Locale.ROOT);
            TimeOfDay raw = applyExplicitAmPm(h, m, suffix);
            return finalizeTime(u, lower, raw);
        }

        Matcher cn = CHINESE_HOUR.matcher(u);
        if (cn.find()) {
            int h = parseChineseHourToken(cn.group(1));
            int m = 0;
            if (cn.group(0).contains("半")) {
                m = 30;
            } else if (cn.group(2) != null) {
                m = Integer.parseInt(cn.group(2));
            }
            return finalizeTime(u, lower, validTime(h, m));
        }

        Matcher ar = ARABIC_HOUR.matcher(u);
        if (ar.find()) {
            int h = Integer.parseInt(ar.group(1));
            int m = 0;
            if (ar.group(0).contains("半")) {
                m = 30;
            } else if (ar.group(2) != null) {
                m = Integer.parseInt(ar.group(2));
            }
            return finalizeTime(u, lower, validTime(h, m));
        }

        Matcher en = EN_AT_TIME.matcher(lower);
        if (en.find()) {
            int h = Integer.parseInt(en.group(1));
            int m = en.group(2) == null ? 0 : Integer.parseInt(en.group(2));
            String ampm = en.group(3);
            TimeOfDay raw = applyExplicitAmPm(h, m, ampm == null ? "" : ampm);
            return finalizeTime(u, lower, raw);
        }
        return null;
    }

    private static TimeOfDay finalizeTime(String u, String lower, TimeOfDay raw) {
        TimeOfDay t = applyDayPeriod(u, lower, raw);
        if (t == null) {
            return null;
        }
        boolean evening =
                u.contains("每晚")
                        || u.contains("每天晚上")
                        || lower.contains("every evening");
        if (evening && t.hour() >= 1 && t.hour() <= 11) {
            return validTime(t.hour() + 12, t.minute());
        }
        return t;
    }

    private static boolean isDaily(String u, String lower) {
        return u.contains("每天")
                || u.contains("每日")
                || u.contains("天天")
                || u.contains("成天")
                || u.contains("每晚")
                || u.contains("每早")
                || u.contains("每天早晨")
                || u.contains("每天早上")
                || u.contains("每天上午")
                || u.contains("每天下午")
                || u.contains("每天晚上")
                || lower.contains("every morning")
                || lower.contains("every evening")
                || EN_DAILY.matcher(lower).find();
    }

    private static String cronDaily(TimeOfDay t) {
        return String.format("0 %d %d * * *", t.minute(), t.hour());
    }

    private static String cronWeekly(TimeOfDay t, int dow) {
        return String.format("0 %d %d * * %d", t.minute(), t.hour(), dow);
    }

    private static String cronMonthly(TimeOfDay t, int dom) {
        return String.format("0 %d %d %d * *", t.minute(), t.hour(), dom);
    }

    private static String cronOnce(TimeOfDay t, int day, int month) {
        return String.format("0 %d %d %d %d *", t.minute(), t.hour(), day, month);
    }

    private static TimeOfDay applyExplicitAmPm(int hour, int minute, String ampmToken) {
        if (ampmToken == null || ampmToken.isBlank()) {
            return validTime(hour, minute);
        }
        String t = ampmToken.toLowerCase(Locale.ROOT);
        boolean pm = t.startsWith("p");
        boolean am = t.startsWith("a");
        int h = hour;
        if (pm && h >= 1 && h <= 11) {
            h += 12;
        }
        if (am && h == 12) {
            h = 0;
        }
        return validTime(h, minute);
    }

    private static TimeOfDay applyDayPeriod(String u, String lower, TimeOfDay raw) {
        if (raw == null) {
            return null;
        }
        boolean pm =
                u.contains("下午")
                        || u.contains("晚上")
                        || u.contains("傍晚")
                        || u.contains("午后")
                        || lower.contains(" pm")
                        || lower.endsWith("pm")
                        || lower.contains("p.m.");
        boolean am =
                u.contains("上午")
                        || u.contains("早上")
                        || u.contains("清晨")
                        || u.contains("早晨")
                        || lower.contains(" am")
                        || lower.endsWith("am")
                        || lower.contains("a.m.");
        int h = raw.hour();
        if (pm && h >= 1 && h <= 11) {
            h += 12;
        }
        if (am && h == 12) {
            h = 0;
        }
        return validTime(h, raw.minute());
    }

    private static TimeOfDay validTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return new TimeOfDay(hour, minute);
    }

    private static int parseChineseHourToken(String token) {
        if (token == null || token.isEmpty()) {
            return -1;
        }
        if (token.startsWith("二十")) {
            String rest = token.substring(2);
            if (rest.isEmpty()) {
                return 20;
            }
            int digit = chineseDigit(rest.charAt(0));
            return digit < 0 ? -1 : 20 + digit;
        }
        if (token.startsWith("十")) {
            String rest = token.substring(1);
            if (rest.isEmpty()) {
                return 10;
            }
            int digit = chineseDigit(rest.charAt(0));
            return digit < 0 ? -1 : 10 + digit;
        }
        if (token.length() == 1) {
            return chineseDigit(token.charAt(0));
        }
        return -1;
    }

    private static int parseDayOfMonthToken(String token) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        if (token.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(token);
        }
        return switch (token) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> -1;
        };
    }

    private static int chineseDigit(char c) {
        return switch (c) {
            case '零' -> 0;
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> -1;
        };
    }

    private static int chineseDow(String c) {
        return switch (c) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            default -> 0;
        };
    }

    private static int englishDow(String token) {
        if (token == null) {
            return 0;
        }
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "mon", "monday" -> 1;
            case "tue", "tuesday" -> 2;
            case "wed", "wednesday" -> 3;
            case "thu", "thursday" -> 4;
            case "fri", "friday" -> 5;
            case "sat", "saturday" -> 6;
            default -> 0;
        };
    }

    static String normalizeChars(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.strip().toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) ('0' + (c - '０')));
            } else if (c == '：') {
                sb.append(':');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
