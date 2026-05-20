package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatStarterPromptSource {
    MANUAL("MANUAL", "运营配置"),
    HOT_TOPIC_DAILY("HOT_TOPIC_DAILY", "每日联网热点"),
    LLM_FOLLOW_UP("LLM_FOLLOW_UP", "模型生成追问");

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
