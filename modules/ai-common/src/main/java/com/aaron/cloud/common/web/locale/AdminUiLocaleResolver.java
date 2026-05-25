package com.aaron.cloud.common.web.locale;

import java.util.Locale;
import org.springframework.lang.Nullable;

/**
 * 管理端界面语言解析：与前端 {@code zh-CN}/{@code en-US}、资源包 {@code *_zh_CN.properties} / {@code *_en.properties} 对齐。
 *
 * <p>其它「按请求语言返回文案」的管理接口可复用 {@link #resolve(String, String, Locale)}，避免各控制器重复解析 {@code ?lang=} 与 {@code Accept-Language}。
 */
public final class AdminUiLocaleResolver {

    /** 与 {@code llm-admin-meta_zh_CN.properties} 等资源包后缀一致 */
    public static final Locale CHINESE_ADMIN_UI = Locale.CHINA;

    public static final Locale ENGLISH_ADMIN_UI = Locale.ENGLISH;

    private AdminUiLocaleResolver() {}

    /**
     * @param langQuery {@code ?lang=} 原始值，可空
     * @param acceptLanguageHeader {@code Accept-Language} 原始值，可空
     * @param springLocale Spring 从请求解析的 {@link Locale}，可空
     */
    public static Locale resolve(
            @Nullable String langQuery, @Nullable String acceptLanguageHeader, @Nullable Locale springLocale) {
        Locale fromQuery = resolveFromLangQuery(langQuery);
        if (fromQuery != null) {
            return fromQuery;
        }
        Locale fromHeader = parseAcceptLanguageFirst(acceptLanguageHeader);
        if (fromHeader != null) {
            return normalize(fromHeader);
        }
        if (springLocale != null && !springLocale.getLanguage().isEmpty()) {
            return normalize(springLocale);
        }
        return CHINESE_ADMIN_UI;
    }

    /**
     * 规范化 {@code ?lang=} 供响应体/诊断头回显；无参数或纯空白时为 {@code null}。
     */
    @Nullable
    public static String normalizedLangQueryRaw(@Nullable String langQuery) {
        if (langQuery == null || langQuery.isBlank()) {
            return null;
        }
        String s = langQuery.trim().replace('\uFEFF', ' ').trim();
        return s.isEmpty() ? null : s;
    }

    @Nullable
    private static Locale resolveFromLangQuery(@Nullable String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        String s = lang.trim().replace('\uFEFF', ' ').trim();
        if ("zh-CN".equalsIgnoreCase(s) || "zh_CN".equalsIgnoreCase(s) || "zh".equalsIgnoreCase(s)) {
            return CHINESE_ADMIN_UI;
        }
        if ("en-US".equalsIgnoreCase(s) || "en_US".equalsIgnoreCase(s) || "en".equalsIgnoreCase(s)) {
            return ENGLISH_ADMIN_UI;
        }
        return null;
    }

    @Nullable
    private static Locale parseAcceptLanguageFirst(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String first = raw.split(",")[0].trim();
        int semi = first.indexOf(';');
        if (semi >= 0) {
            first = first.substring(0, semi).trim();
        }
        if (first.isEmpty() || "*".equals(first)) {
            return null;
        }
        Locale loc = Locale.forLanguageTag(first.replace('_', '-'));
        return loc.getLanguage().isEmpty() ? null : loc;
    }

    private static Locale normalize(Locale loc) {
        String language = loc.getLanguage().toLowerCase(Locale.ROOT);
        if (language.startsWith("zh")) {
            return CHINESE_ADMIN_UI;
        }
        if (language.startsWith("en")) {
            return ENGLISH_ADMIN_UI;
        }
        return CHINESE_ADMIN_UI;
    }
}
