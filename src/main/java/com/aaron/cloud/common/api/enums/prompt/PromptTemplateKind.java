package com.aaron.cloud.common.api.enums.prompt;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PromptTemplateKind {
    SYSTEM("SYSTEM"),
    USER("USER"),
    FRAGMENT("FRAGMENT"),
    QUERY("QUERY");

    @EnumValue
    private final String code;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PromptTemplateKind fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("promptKind required");
        }
        String n = raw.trim().toUpperCase();
        for (PromptTemplateKind e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown prompt kind: " + raw);
    }
}
