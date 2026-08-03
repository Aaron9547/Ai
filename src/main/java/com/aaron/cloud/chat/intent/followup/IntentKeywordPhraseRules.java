package com.aaron.cloud.chat.intent.followup;

import com.aaron.cloud.common.api.enums.chat.ChatIntentKeywordKind;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.aaron.cloud.common.chat.entity.ChatIntentKeyword;
import java.util.ArrayList;
import java.util.List;

/** 从 {@code chat_intent_keyword} 提取可展示的追问/续轮话术（多意图共用）。 */
public final class IntentKeywordPhraseRules {

    private static final List<String> TRAVEL_PLAN_CONTINUE_DEFAULTS = List.of("继续", "安排行程");

    private static final List<String> REMINDER_CANCEL_DEFAULTS = List.of("取消提醒", "关闭提醒");

    private IntentKeywordPhraseRules() {}

    /** PLAN_CONTINUE 关键词；无库内配置时用 {@code defaults}，仍为空则回退 {@link #TRAVEL_PLAN_CONTINUE_DEFAULTS}。 */
    public static List<String> planContinuePhrases(
            List<ChatIntentKeyword> keywords, String sessionRoundName, List<String> defaults) {
        List<String> fromDb =
                keywords.stream()
                        .filter(k -> k.getKeywordKind() == ChatIntentKeywordKind.PLAN_CONTINUE)
                        .filter(k -> k.getEnabled() == ToggleState.ON)
                        .filter(k -> matchesRound(k, sessionRoundName))
                        .map(ChatIntentKeyword::getPhrase)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        if (sessionRoundName != null
                && !sessionRoundName.isBlank()
                && "PLAN".equalsIgnoreCase(sessionRoundName.trim())
                && defaults != null
                && !defaults.isEmpty()) {
            return List.copyOf(defaults);
        }
        if (sessionRoundName != null && "PLAN".equalsIgnoreCase(sessionRoundName.trim())) {
            return TRAVEL_PLAN_CONTINUE_DEFAULTS;
        }
        return List.of();
    }

    public static List<String> cancelPhrases(List<ChatIntentKeyword> keywords) {
        List<String> fromDb =
                keywords.stream()
                        .filter(k -> k.getKeywordKind() == ChatIntentKeywordKind.CANCEL)
                        .filter(k -> k.getEnabled() == ToggleState.ON)
                        .map(ChatIntentKeyword::getPhrase)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (!fromDb.isEmpty()) {
            return fromDb;
        }
        return REMINDER_CANCEL_DEFAULTS;
    }

    /** 限制 chip 数量，避免挤占对话区。 */
    public static List<String> limitForUi(List<String> texts, int max) {
        if (texts == null || texts.isEmpty() || max <= 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String t : texts) {
            if (t == null || t.isBlank()) {
                continue;
            }
            out.add(t.strip());
            if (out.size() >= max) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static boolean matchesRound(ChatIntentKeyword k, String sessionRoundName) {
        if (sessionRoundName == null || sessionRoundName.isBlank()) {
            return true;
        }
        String target = k.getTargetRound();
        if (target == null || target.isBlank()) {
            return true;
        }
        return sessionRoundName.trim().equalsIgnoreCase(target.trim());
    }
}
