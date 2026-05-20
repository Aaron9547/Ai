package com.aaron.cloud.rag;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 正文根节点选择（对齐 ly-ai {@code JsoupHelper#pickMainContentRoot}，并覆盖 VSB/门户 CMS）。
 */
public final class RagWebContentRootPicker {

    /** 高校 CMS 常见正文容器：命中即用，不要求最小字数（图文页正文可能较短）。 */
    private static final String[] CMS_CONTENT_SELECTORS = {
        "#main",
        "#vsb_newscontent",
        "[id^=vsb_content]",
        ".vsb_content",
        "#zoom",
        ".TRS_Editor",
        ".v_news_content",
        "#article_content",
        ".article-content",
        ".article_txt",
        ".article_tex",
        ".news_text",
        ".news_con",
        ".detail_con",
        ".article_detail",
        ".news_detail",
        ".c194344_content"
    };

    /** 通用语义/栏目容器：需有一定文本，避免误选空壳 div。 */
    private static final String[] SEMANTIC_CONTENT_SELECTORS = {
        "article",
        "[role=main]",
        "main",
        ".detail",
        ".entry-content",
        ".post-content",
        "#dnn_ContentPane",
        "#content",
        ".content"
    };

    private static final int MIN_SEMANTIC_TEXT_LEN = 80;

    private RagWebContentRootPicker() {}

    public static Element pick(Document doc, RagWebCrawlExtractConfig cfg) {
        if (doc == null) {
            return null;
        }
        if (cfg != null && cfg.getContentSelector() != null && !cfg.getContentSelector().isBlank()) {
            Element custom = doc.selectFirst(cfg.getContentSelector().trim());
            if (custom != null) {
                return custom;
            }
        }
        for (String sel : CMS_CONTENT_SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                return el;
            }
        }
        for (String sel : SEMANTIC_CONTENT_SELECTORS) {
            Element el = doc.selectFirst(sel);
            if (el != null && el.text().trim().length() >= MIN_SEMANTIC_TEXT_LEN) {
                return el;
            }
        }
        return doc.body();
    }
}
