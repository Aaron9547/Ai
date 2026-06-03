package com.aaron.cloud.chat.intent.followup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.enums.chat.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentFollowUpPromptCatalogTest {

    @Test
    void travelAfterDocComplete_usesDefaultsWhenNoKeywords() {
        List<String> prompts =
                IntentFollowUpPromptCatalog.resolve(
                        IntentFollowUpContext.travelAfterDocComplete(List.of()));
        assertTrue(prompts.contains("继续"));
        assertTrue(prompts.size() <= 3);
    }

    @Test
    void reminderAfterSuccess_usesCancelDefaults() {
        List<String> prompts =
                IntentFollowUpPromptCatalog.resolve(
                        IntentFollowUpContext.reminderAfterSuccess(List.of()));
        assertEquals(List.of("取消提醒", "关闭提醒"), prompts);
    }

    @Test
    void noneStage_returnsEmpty() {
        assertTrue(IntentFollowUpPromptCatalog.resolve(IntentFollowUpContext.none()).isEmpty());
    }

    @Test
    void travelPrefersDbPlanContinue() {
        ChatIntentKeyword k = new ChatIntentKeyword();
        k.setKeywordKind(ChatIntentKeywordKind.PLAN_CONTINUE);
        k.setEnabled(ToggleState.ON);
        k.setTargetRound("PLAN");
        k.setPhrase("帮我订酒店");
        List<String> prompts =
                IntentFollowUpPromptCatalog.resolve(
                        IntentFollowUpContext.travelAfterDocComplete(List.of(k)));
        assertEquals(List.of("帮我订酒店"), prompts);
    }
}
