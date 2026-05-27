package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.dto.WebSearchReferenceView;

/**
 * 联网检索单条引用：供主模型注入、SSE 回显与 meta 落库。
 *
 * @param extraJson 可选扩展 JSON 字符串（如 Ark {@code references[].extra} 或 {@code search_plugin_data} 子树）。
 */
public record WebSearchReference(
        String title,
        String url,
        String snippet,
        String siteName,
        String logoUrl,
        String publishTime,
        String extraJson,
        /** 检索源码（如 {@code BAIDU_NEWS_HTML}、{@code VOLCENGINE_ARK_BOT}），供知识库按源分组。 */
        String sourceKey) {

    /** 三字段兼容（旧 Provider / 单测）。 */
    public static WebSearchReference ofTitleUrlSnippet(String title, String url, String snippet) {
        return new WebSearchReference(
                title == null ? "" : title,
                url == null ? "" : url,
                snippet == null ? "" : snippet,
                null,
                null,
                null,
                null,
                null);
    }

    public WebSearchReferenceView toView() {
        return new WebSearchReferenceView(
                title(),
                url(),
                snippet(),
                siteName(),
                logoUrl(),
                publishTime(),
                extraJson());
    }
}
