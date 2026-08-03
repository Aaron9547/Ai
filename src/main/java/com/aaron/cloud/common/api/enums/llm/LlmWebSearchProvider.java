package com.aaron.cloud.common.api.enums.llm;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * {@link LlmModelKind#WEB_SEARCH} 行在 {@code llm_model.integration_backend} 存的对接实现（仅火山 Ark Bot 联网模型）。
 *
 * <p>DDG / 维基 / Google News RSS / 百度新闻等见 {@link WebSearchFixedSource}，由代码注册，不在模型表配置。
 */
@Getter
@RequiredArgsConstructor
public enum LlmWebSearchProvider {
    VOLCENGINE_ARK_BOT("VOLCENGINE_ARK_BOT", true);

    @EnumValue private final String code;
    private final boolean requiresApiKey;

    public static LlmWebSearchProvider fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (LlmWebSearchProvider p : values()) {
            if (p.code.equalsIgnoreCase(s) || p.name().equalsIgnoreCase(s)) {
                return p;
            }
        }
        return null;
    }
}
