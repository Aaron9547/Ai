package com.aaron.cloud.chat.intent;

import com.aaron.cloud.chat.PipelineLogZh;

/** 意图子模块日志的中文可读标签。 */
public final class IntentLogZh {

    private IntentLogZh() {}

    public static String yesNo(boolean value) {
        return PipelineLogZh.yesNo(value);
    }

    /** 出差报销意图阶段（{@code TravelReimbursementRound} 存储名）。 */
    public static String travelRound(String roundName) {
        if (roundName == null || roundName.isBlank()) {
            return "未知";
        }
        return switch (roundName.trim().toUpperCase()) {
            case "DOC" -> "材料上传(DOC)";
            case "PLAN" -> "行程规划(PLAN)";
            default -> roundName;
        };
    }

    /** 一句话提醒意图阶段（{@code OneSentenceReminderRound} 存储名）。 */
    public static String reminderRound(String roundName) {
        if (roundName == null || roundName.isBlank()) {
            return "未知";
        }
        return switch (roundName.trim().toUpperCase()) {
            case "CREATE" -> "新建提醒";
            case "CANCEL" -> "取消提醒";
            case "CANCEL_SELECT" -> "选择取消编号";
            default -> roundName;
        };
    }
}
