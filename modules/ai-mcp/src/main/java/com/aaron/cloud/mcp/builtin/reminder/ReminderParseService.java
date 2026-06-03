package com.aaron.cloud.mcp.builtin.reminder;

import com.aaron.cloud.common.api.mcp.reminder.ReminderCancelSelectionSupport;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ActiveReminderRef;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReminderParseService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 「每天/每日」但未写具体时刻时的默认小时（Asia/Shanghai）。 */
    private static final int DEFAULT_DAILY_HOUR_WHEN_MISSING = 9;

    private static final Pattern DAILY =
            Pattern.compile("(?:每[天日]|每天|每日).*?(\\d{1,2})\\s*(?:点|时|:)");
    private static final Pattern WEEKLY =
            Pattern.compile("每(?:周|星期)([一二三四五六日天]).*?(\\d{1,2})\\s*(?:点|时|:)");
    private static final Pattern MONTHLY =
            Pattern.compile("每(?:月|个月)(\\d{1,2})\\s*日.*?(\\d{1,2})\\s*(?:点|时|:)");
    private static final Pattern ONCE_DATE =
            Pattern.compile("(\\d{4})[\\-/年](\\d{1,2})[\\-/月](\\d{1,2}).*?(\\d{1,2})\\s*(?:点|时|:)");
    private static final Pattern TOMORROW =
            Pattern.compile("明[天日].*?(\\d{1,2})\\s*(?:点|时|:)");
    private static final Pattern HOUR_ONLY = Pattern.compile("(\\d{1,2})\\s*(?:点|时|:)");

    public ReminderParseResponse parse(ReminderParseRequest req) {
        if (req == null) {
            return err("请求为空");
        }
        String utterance = normalize(req.utterance());
        if (utterance.isEmpty()) {
            return err("请说明要提醒的内容与时间");
        }
        String action = req.action() == null ? "AUTO" : req.action().trim().toUpperCase(Locale.ROOT);
        if ("AUTO".equals(action)) {
            if (containsCancelLexicon(utterance)) {
                action = "CANCEL";
            } else if (containsRemindLexicon(utterance)) {
                action = "CREATE";
            } else {
                return err("无法识别是创建还是取消提醒，请说明「提醒我…」或「取消提醒」");
            }
        }
        if ("CANCEL".equals(action)) {
            return parseCancel(utterance, req.activeReminders());
        }
        if ("CREATE".equals(action)) {
            return parseCreate(utterance);
        }
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                "NOOP",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "请说明要创建或取消哪条提醒",
                null);
    }

    private ReminderParseResponse parseCancel(String utterance, List<ActiveReminderRef> active) {
        return ReminderCancelSelectionSupport.resolveCancel(utterance, active);
    }

    private ReminderParseResponse parseCreate(String utterance) {
        ScheduleExtracted ex = extractSchedule(utterance);
        if (ex == null) {
            return err("请说明提醒时间，例如「每天8点」或「每周一9点」");
        }
        String actionText = extractActionText(utterance);
        if (actionText.isBlank()) {
            return err("请说明要提醒做什么事");
        }
        String title = actionText.length() > 64 ? actionText.substring(0, 64) : actionText;
        String userMsg =
                switch (ex.type) {
                    case "DAILY" -> "已设置每天 " + ex.hour + ":" + String.format("%02d", ex.minute) + " 邮件提醒："
                            + title;
                    case "WEEKLY" -> "已设置每周 " + ex.dowLabel + " " + ex.hour + ":"
                            + String.format("%02d", ex.minute) + " 邮件提醒：" + title;
                    case "MONTHLY" -> "已设置每月 " + ex.dayOfMonth + " 日 " + ex.hour + ":"
                            + String.format("%02d", ex.minute) + " 邮件提醒：" + title;
                    default -> "已设置提醒：" + title;
                };
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                "CREATE",
                title,
                actionText,
                ex.type,
                ex.cron,
                ex.endsAt,
                null,
                null,
                null,
                userMsg,
                null);
    }

    private static ScheduleExtracted extractSchedule(String utterance) {
        Matcher w = WEEKLY.matcher(utterance);
        if (w.find()) {
            int dow = chineseDow(w.group(1));
            int hour = clampHour(Integer.parseInt(w.group(2)));
            String cron = String.format("0 0 %d * * %d", hour, dow);
            return new ScheduleExtracted("WEEKLY", cron, null, hour, 0, dow, 0, w.group(1));
        }
        Matcher m = MONTHLY.matcher(utterance);
        if (m.find()) {
            int dom = Integer.parseInt(m.group(1));
            int hour = clampHour(Integer.parseInt(m.group(2)));
            String cron = String.format("0 0 %d %d * *", hour, dom);
            return new ScheduleExtracted("MONTHLY", cron, null, hour, 0, 0, dom, null);
        }
        Matcher d = DAILY.matcher(utterance);
        boolean dailyMatched = d.find();
        if (dailyMatched || utterance.contains("每天") || utterance.contains("每日")) {
            int hour =
                    dailyMatched
                            ? clampHour(Integer.parseInt(d.group(1)))
                            : extractFallbackHour(utterance);
            if (hour < 0) {
                hour = DEFAULT_DAILY_HOUR_WHEN_MISSING;
            }
            String cron = String.format("0 0 %d * * *", hour);
            return new ScheduleExtracted("DAILY", cron, null, hour, 0, 0, 0, null);
        }
        Matcher od = ONCE_DATE.matcher(utterance);
        if (od.find()) {
            int y = Integer.parseInt(od.group(1));
            int mo = Integer.parseInt(od.group(2));
            int da = Integer.parseInt(od.group(3));
            int hour = clampHour(Integer.parseInt(od.group(4)));
            LocalDateTime at = LocalDateTime.of(y, mo, da, hour, 0);
            if (at.isBefore(LocalDateTime.now(ZONE))) {
                return null;
            }
            String cron = String.format("0 0 %d %d %d *", hour, da, mo);
            return new ScheduleExtracted("ONCE", cron, at.toString(), hour, 0, 0, 0, null);
        }
        Matcher tm = TOMORROW.matcher(utterance);
        if (tm.find()) {
            int hour = clampHour(Integer.parseInt(tm.group(1)));
            LocalDate day = LocalDate.now(ZONE).plusDays(1);
            LocalDateTime at = LocalDateTime.of(day, LocalTime.of(hour, 0));
            String cron = String.format("0 0 %d %d %d *", hour, day.getDayOfMonth(), day.getMonthValue());
            return new ScheduleExtracted("ONCE", cron, at.toString(), hour, 0, 0, 0, null);
        }
        if (utterance.contains("一次") || utterance.contains("某天")) {
            int hour = extractFallbackHour(utterance);
            if (hour < 0) {
                return null;
            }
            LocalDateTime at = LocalDateTime.now(ZONE).plusDays(1).withHour(hour).withMinute(0).withSecond(0);
            LocalDate day = at.toLocalDate();
            String cron = String.format("0 0 %d %d %d *", hour, day.getDayOfMonth(), day.getMonthValue());
            return new ScheduleExtracted("ONCE", cron, at.toString(), hour, 0, 0, 0, null);
        }
        return null;
    }

    private static int extractFallbackHour(String utterance) {
        Matcher h = HOUR_ONLY.matcher(utterance);
        if (h.find()) {
            return clampHour(Integer.parseInt(h.group(1)));
        }
        return -1;
    }

    private static String extractActionText(String utterance) {
        String t = utterance;
        t = t.replaceAll("提醒我|请提醒|帮我提醒|定时提醒|邮件提醒|每天|每日|每周|每月|一次", "");
        t = t.replaceAll("\\d{1,2}\\s*(?:点|时|:)|\\d{4}[\\-/年]\\d{1,2}[\\-/月]\\d{1,2}", "");
        t = t.replaceAll("每[周月天日星期一二三四五六日]+", "");
        return t.trim();
    }

    private static boolean containsCancelLexicon(String u) {
        return u.contains("取消提醒")
                || u.contains("关闭提醒")
                || u.contains("不要提醒")
                || u.contains("停止提醒")
                || u.contains("取消通知");
    }

    private static boolean containsRemindLexicon(String u) {
        return u.contains("提醒我") || u.contains("定时提醒") || u.contains("邮件提醒");
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

    private static int clampHour(int h) {
        if (h < 0 || h > 23) {
            return -1;
        }
        return h;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip();
    }

    private static ReminderParseResponse err(String msg) {
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                msg);
    }

    private record ScheduleExtracted(
            String type,
            String cron,
            String endsAt,
            int hour,
            int minute,
            int dow,
            int dayOfMonth,
            String dowLabel) {}
}
