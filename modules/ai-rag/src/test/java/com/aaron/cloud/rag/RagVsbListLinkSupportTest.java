package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class RagVsbListLinkSupportTest {

    @Test
    void extractListArticleLinks_parsesVsbTuwenList() throws Exception {
        Path html = Path.of("target/yxxsfc.htm");
        var doc = Jsoup.parse(html.toFile(), "https://sxy.zua.edu.cn/xsgz/yxxsfc.htm");
        var links = RagVsbListLinkSupport.extractListArticleLinks(doc, "https://sxy.zua.edu.cn/xsgz/yxxsfc.htm");
        assertEquals(10, links.size());
        assertTrue(
                links.stream()
                        .anyMatch(
                                l ->
                                        l.href().contains("/info/1157/6633.htm")
                                                && "张壹".equals(l.title())));
    }

    @Test
    void looksLikeArticle_acceptsInfoUrlWithoutAnchorText() {
        assertTrue(
                RagArticleLinkHeuristics.looksLikeArticle(
                        new RagCrawlPageLink("https://sxy.zua.edu.cn/info/1157/6633.htm", "")));
    }
}
