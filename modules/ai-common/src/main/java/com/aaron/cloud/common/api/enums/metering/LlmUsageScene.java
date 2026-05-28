package com.aaron.cloud.common.api.enums.metering;

import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptSource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** LLM token 计量业务场景（写入 {@code metering_usage_event.ref_json.usageScene}）。 */
@Getter
@RequiredArgsConstructor
public enum LlmUsageScene {
    CHAT("CHAT", "对话"),
    DAILY_RECOMMEND("DAILY_RECOMMEND", "今日智能洞察"),
    HOT_TOPIC_DAILY("HOT_TOPIC_DAILY", "每日热点"),
    KNOWLEDGE_PLANET("KNOWLEDGE_PLANET", "知识星球"),
    MEMORY_ABSTRACT("MEMORY_ABSTRACT", "记忆摘要"),
    CONVERSATION_DIGEST("CONVERSATION_DIGEST", "对话摘要"),
    LLM_FOLLOW_UP("LLM_FOLLOW_UP", "猜你想问");

    private final String code;
    private final String label;

    public static LlmUsageScene fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().toUpperCase();
        for (LlmUsageScene e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        return null;
    }

    /** 联网沉淀来源 → 计量场景；对话联网归 {@link #CHAT}。 */
    public static LlmUsageScene fromStarterSource(ChatStarterPromptSource starter, long conversationId) {
        if (starter == ChatStarterPromptSource.DAILY_RECOMMEND) {
            return DAILY_RECOMMEND;
        }
        if (starter == ChatStarterPromptSource.HOT_TOPIC_DAILY) {
            return HOT_TOPIC_DAILY;
        }
        if (conversationId > 0L) {
            return CHAT;
        }
        return null;
    }
}
