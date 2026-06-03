package com.aaron.cloud.chat.intent.followup;

import com.aaron.cloud.common.api.enums.chat.ChatIntentHandlerKind;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import java.util.List;

/** 追问解析入参：各意图处理器仅需在收尾时声明阶段，不必各自拼装 chip 文案。 */
public record IntentFollowUpContext(
        ChatIntentHandlerKind handlerKind,
        IntentFollowUpStage stage,
        List<ChatIntentKeyword> keywords,
        String flowRound) {

    public IntentFollowUpContext {
        if (keywords == null) {
            keywords = List.of();
        }
    }

    public static IntentFollowUpContext none() {
        return new IntentFollowUpContext(null, IntentFollowUpStage.NONE, List.of(), null);
    }

    public static IntentFollowUpContext travelAfterDocComplete(List<ChatIntentKeyword> keywords) {
        return new IntentFollowUpContext(
                ChatIntentHandlerKind.TRAVEL_REIMBURSEMENT,
                IntentFollowUpStage.TRAVEL_AFTER_DOC_COMPLETE,
                keywords,
                "PLAN");
    }

    public static IntentFollowUpContext reminderAfterSuccess(List<ChatIntentKeyword> keywords) {
        return new IntentFollowUpContext(
                ChatIntentHandlerKind.ONE_SENTENCE_REMINDER,
                IntentFollowUpStage.REMINDER_AFTER_SUCCESS,
                keywords,
                null);
    }
}
