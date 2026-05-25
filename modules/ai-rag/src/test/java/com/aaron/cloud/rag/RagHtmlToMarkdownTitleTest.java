package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RagHtmlToMarkdownTitleTest {

    @Test
    void resolveDocumentTitle_prefersMarkdownH1OverSiteSuffix() {
        String md = "# 张壹\n\n正文内容。";
        String title =
                RagHtmlToMarkdown.resolveDocumentTitle(
                        new RagHtmlToMarkdown.ParsedPage("张壹-郑州航空工业管理学院商学院", md),
                        "https://sxy.zua.edu.cn/info/1157/6633.htm",
                        null);
        assertEquals("张壹", title);
    }

    @Test
    void resolveDocumentTitle_usesListHintWhenPageTitleMissing() {
        String title =
                RagHtmlToMarkdown.resolveDocumentTitle(
                        new RagHtmlToMarkdown.ParsedPage("", "正文无标题"),
                        "https://sxy.zua.edu.cn/info/1157/6633.htm",
                        "张壹");
        assertEquals("张壹", title);
    }
}
