package com.aaron.cloud.common.api.enums.message;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageSceneCode {
    REGISTER_VERIFICATION("REGISTER_VERIFICATION", "注册验证码"),
    KNOWLEDGE_PLANET_WEEKLY("KNOWLEDGE_PLANET_WEEKLY", "知识星球周报"),
    SMS_LOGIN("SMS_LOGIN", "短信登录验证码");

    @EnumValue
    private final String code;

    private final String label;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static MessageSceneCode fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("sceneCode required");
        }
        String n = raw.trim().toUpperCase();
        for (MessageSceneCode e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown message scene: " + raw);
    }
}
