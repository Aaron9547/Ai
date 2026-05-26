package com.aaron.cloud.common.api.enums.chat;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatStarterPromptScene {
    EMPTY("EMPTY", "空会话起步"),
    FOLLOW_UP("FOLLOW_UP", "助手回复后追问"),
    WEB_KNOWLEDGE("WEB_KNOWLEDGE", "联网检索知识库");

    @EnumValue
    private final String code;

    private final String label;

    public static ChatStarterPromptScene fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("scene required");
        }
        String n = raw.trim().toUpperCase();
        for (ChatStarterPromptScene e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown starter prompt scene: " + raw);
    }
}
