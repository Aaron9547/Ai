package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RagArticleLinkHeuristicsTest {

    @Test
    void looksLikeArticle_acceptsTypicalNewsPath() {
        assertTrue(
                RagArticleLinkHeuristics.looksLikeArticle(
                        new RagCrawlPageLink("https://www.example.edu.cn/news/2024/0301/12345.htm", "学校召开工作会议")));
    }

    @Test
    void looksLikeArticle_rejectsHomeNav() {
        assertFalse(RagArticleLinkHeuristics.looksLikeArticle(new RagCrawlPageLink("https://www.example.edu.cn/", "首页")));
        assertFalse(
                RagArticleLinkHeuristics.looksLikeArticle(
                        new RagCrawlPageLink("https://www.example.edu.cn/list.htm", "下一页")));
    }

    @Test
    void looksLikeNextPage_detectsChineseLabel() {
        assertTrue(
                RagArticleLinkHeuristics.looksLikeNextPage(
                        new RagCrawlPageLink("https://www.example.edu.cn/news/list2.htm", "下一页")));
    }
}
