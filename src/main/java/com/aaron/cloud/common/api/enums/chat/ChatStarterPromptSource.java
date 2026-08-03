package com.aaron.cloud.common.api.enums.chat;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatStarterPromptSource {
    MANUAL("MANUAL", "运营配置"),
    HOT_TOPIC_DAILY("HOT_TOPIC_DAILY", "每日联网热点"),
    DAILY_RECOMMEND("DAILY_RECOMMEND", "今日智能洞察"),
    LLM_FOLLOW_UP("LLM_FOLLOW_UP", "模型生成追问"),
    WEB_SEARCH_GROUNDING("WEB_SEARCH_GROUNDING", "对话联网检索沉淀"),
    KNOWLEDGE_PLANET_WEEKLY("KNOWLEDGE_PLANET_WEEKLY", "知识星球周报荐书检索");

    @EnumValue
    private final String code;

    private final String label;

    public static ChatStarterPromptSource fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("source required");
        }
        String n = raw.trim().toUpperCase();
        for (ChatStarterPromptSource e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown starter prompt source: " + raw);
    }
}
