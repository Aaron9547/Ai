package com.aaron.cloud.common.api.enums.prompt;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PromptTemplateDomain {
    CHAT("CHAT"),
    MEMORY("MEMORY"),
    RAG("RAG"),
    WEB("WEB"),
    PLANET("PLANET"),
    STARTER("STARTER"),
    GUARD("GUARD");

    @EnumValue
    private final String code;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PromptTemplateDomain fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("domain required");
        }
        String n = raw.trim().toUpperCase();
        for (PromptTemplateDomain e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown prompt domain: " + raw);
    }
}
