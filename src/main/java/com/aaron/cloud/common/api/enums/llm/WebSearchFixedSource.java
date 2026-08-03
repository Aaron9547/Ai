package com.aaron.cloud.common.api.enums.llm;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 内置免费/直连联网检索源（代码注册，不占用 {@link LlmModelKind#WEB_SEARCH} 模型行）。
 * 租户在「外观与模型调用」通过 {@code WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON} 勾选启用。
 * 国内机房建议优先 {@link #BAIDU_NEWS_HTML}；DDG / 维基 / Google News 需可访问境外网络。
 */
@Getter
@RequiredArgsConstructor
public enum WebSearchFixedSource {
    DUCKDUCKGO_HTML("DUCKDUCKGO_HTML"),
    WIKIPEDIA_REST("WIKIPEDIA_REST"),
    GOOGLE_NEWS_RSS("GOOGLE_NEWS_RSS"),
    BAIDU_NEWS_HTML("BAIDU_NEWS_HTML");

    private final String code;

    public static WebSearchFixedSource fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (WebSearchFixedSource v : values()) {
            if (v.code.equalsIgnoreCase(s) || v.name().equalsIgnoreCase(s)) {
                return v;
            }
        }
        return null;
    }
}
