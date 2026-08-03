package com.aaron.cloud.common.web.http;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;

/**
 * 管理端「内容协商」相关响应头：与 CORS {@code exposedHeaders} 使用同一常量，避免散落魔法字符串。
 *
 * <p>当前用于 LLM 模型元数据接口；其它返回 MessageSource 文案的管理 API 可复用 {@link #applyLlmModelMeta(HttpServletResponse, Locale, String)} 或在本类中增加语义化别名方法。
 */
public final class AdminUiNegotiationHeaders {

    public static final String X_LLM_META_LOCALE = "X-Llm-Meta-Locale";

    public static final String X_LLM_META_LANG_PARAM = "X-Llm-Meta-Lang-Param";

    private AdminUiNegotiationHeaders() {}

    /**
     * LLM 管理元数据接口：写入语言标签、Vary、以及便于排查的自定义头。
     *
     * @param langParamRaw {@link com.aaron.cloud.common.web.locale.AdminUiLocaleResolver#normalizedLangQueryRaw(String)} 的结果；无 {@code lang} 查询时为 {@code null}，此时 {@code X-Llm-Meta-Lang-Param} 写空串。
     */
    public static void applyLlmModelMeta(HttpServletResponse response, Locale effectiveLocale, @Nullable String langParamRaw) {
        String tag = effectiveLocale.toLanguageTag();
        response.setHeader(X_LLM_META_LOCALE, tag);
        response.setHeader(X_LLM_META_LANG_PARAM, langParamRaw == null ? "" : langParamRaw);
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, tag);
        response.setHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
    }
}
