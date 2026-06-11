package com.aaron.cloud.mcp.builtin.reminder;

import com.aaron.cloud.common.api.mcp.reminder.ReminderCancelSelectionSupport;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ActiveReminderRef;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import com.aaron.cloud.mcp.builtin.reminder.ReminderScheduleExtractor.ScheduleExtracted;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ReminderParseService {

    public ReminderParseResponse parse(ReminderParseRequest req) {
        if (req == null) {
            return err("请求为空", null);
        }
        String locale = req.locale();
        String utterance = ReminderScheduleExtractor.normalizeChars(req.utterance());
        if (utterance.isEmpty()) {
            return err(msgMissingContent(locale), locale);
        }
        String action = req.action() == null ? "AUTO" : req.action().trim().toUpperCase(Locale.ROOT);
        if ("AUTO".equals(action)) {
            if (ReminderScheduleExtractor.containsCancelLexicon(utterance)) {
                action = "CANCEL";
            } else if (ReminderScheduleExtractor.containsCreateLexicon(utterance)) {
                action = "CREATE";
            } else {
                return err(msgUnrecognizedOp(locale), locale);
            }
        }
        if ("CANCEL".equals(action)) {
            return ReminderCancelSelectionSupport.resolveCancel(utterance, req.activeReminders(), locale);
        }
        if ("CREATE".equals(action)) {
            return parseCreate(utterance, locale);
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
                msgChooseReminder(locale),
                null);
    }

    private ReminderParseResponse parseCreate(String utterance, String locale) {
        ScheduleExtracted ex = ReminderScheduleExtractor.extractSchedule(utterance);
        if (ex == null) {
            return err(msgMissingSchedule(locale), locale);
        }
        String actionText = ReminderScheduleExtractor.extractActionText(utterance);
        if (actionText.isBlank()) {
            return err(msgMissingAction(locale), locale);
        }
        String title = actionText.length() > 64 ? actionText.substring(0, 64) : actionText;
        String userMsg = ReminderScheduleExtractor.formatCreateUserMessage(locale, ex, title);
        return new ReminderParseResponse(
                ReminderParseContracts.CONTRACT_VERSION,
                "CREATE",
                title,
                actionText,
                ex.type(),
                ex.cron(),
                ex.endsAt(),
                null,
                null,
                null,
                userMsg,
                null);
    }

    private static String msgMissingContent(String locale) {
        return ReminderScheduleExtractor.prefersEnglish(locale)
                ? "Please describe what to remind and when."
                : "请说明要提醒的内容与时间";
    }

    private static String msgUnrecognizedOp(String locale) {
        return ReminderScheduleExtractor.prefersEnglish(locale)
                ? "Say \"remind me to …\" to create, or \"cancel reminder\" to remove one."
                : "无法识别是创建还是取消提醒，请说明「提醒我…」或「取消提醒」";
    }

    private static String msgMissingSchedule(String locale) {
        return ReminderScheduleExtractor.prefersEnglish(locale)
                ? "Please include a time, e.g. \"every day at 8\" or \"every Monday at 9am\"."
                : "请说明提醒时间，例如「每天8点」或「每周一9点」";
    }

    private static String msgMissingAction(String locale) {
        return ReminderScheduleExtractor.prefersEnglish(locale)
                ? "Please say what you want to be reminded to do."
                : "请说明要提醒做什么事";
    }

    private static String msgChooseReminder(String locale) {
        return ReminderScheduleExtractor.prefersEnglish(locale)
                ? "Please specify which reminder to create or cancel."
                : "请说明要创建或取消哪条提醒";
    }

    private static ReminderParseResponse err(String msg, String locale) {
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
}
