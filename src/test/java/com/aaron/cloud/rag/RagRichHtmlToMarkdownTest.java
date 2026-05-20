package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class RagRichHtmlToMarkdownTest {

    @Test
    void buildRichMarkdown_emitsImageAndTable() {
        String html =
                "<div id=\"vsb_newscontent\">"
                        + "<p>简介</p>"
                        + "<img src=\"/photo/a.jpg\" alt=\"头像\"/>"
                        + "<table><tr><th>列</th></tr><tr><td>值</td></tr></table>"
                        + "</div>";
        var doc = Jsoup.parse(html, "https://sxy.zua.edu.cn/info/1157/1.htm");
        var root = doc.selectFirst("#vsb_newscontent");
        String md = RagRichHtmlToMarkdown.buildRichMarkdown("张三", root, doc.baseUri());
        assertTrue(md.contains("![头像](https://sxy.zua.edu.cn/photo/a.jpg"), md);
        assertTrue(md.contains("| 列 |"), md);
        assertTrue(md.contains("# 张三"), md);
    }

    @Test
    void buildRichMarkdown_cnrZhuantiHeadLine_splitAnchors() {
        String html =
                "<div id=\"main\"><div class=\"wrapper\">"
                        + "<h1>热门专题</h1>"
                        + "<div class=\"head-line\">"
                        + "<div class=\"head-line-img\">"
                        + "<a href=\"https://news.cnr.cn/2025zt/szqh/\" target=\"_blank\">"
                        + "<img width=\"800\" height=\"363\""
                        + " src=\"https://www.cnr.cn/zhuanti/news/20251023/W020260313562413529936.jpg\" alt=\"\">"
                        + "</a></div>"
                        + "<a href=\"https://news.cnr.cn/2025zt/szqh/\" target=\"_blank\">"
                        + "<div style=\"height:360px;\">"
                        + "<h2 style=\"margin: 20px 0 30px 0;\">学习贯彻党的二十届四中全会精神</h2>"
                        + "<p style=\"text-indent: 40px;\">中国共产党第二十届中央委员会第四次全体会议，于2025年10月20日至23日在北京举行。</p>"
                        + "</div></a></div></div></div>";
        var doc = Jsoup.parse(html, "https://www.cnr.cn/zhuanti/news/");
        var root = doc.selectFirst("#main");
        String md = RagRichHtmlToMarkdown.buildRichMarkdown("热门专题", root, doc.baseUri());
        assertTrue(
                md.contains(
                        "[![](https://www.cnr.cn/zhuanti/news/20251023/W020260313562413529936.jpg)](https://news.cnr.cn/2025zt/szqh/)"),
                md);
        assertFalse(md.contains("\\!\\["), md);
        assertTrue(md.contains("### 学习贯彻党的二十届四中全会精神"), md);
        assertTrue(md.contains("中国共产党第二十届中央委员会第四次全体会议"), md);
        assertFalse(md.contains("[### 学习贯彻"), md);
    }

    @Test
    void repairBrokenMarkdownLinkSyntax_fixesLegacyOutput() {
        String broken =
                "# 热门专题\n\n"
                        + "[\\!\\[\\](https://www.cnr.cn/zhuanti/news/w.jpg)](https://news.cnr.cn/2025zt/szqh/)\n\n"
                        + "[### 学习贯彻党的二十届四中全会精神\n\n"
                        + "中国共产党第二十届中央委员会第四次全体会议。](https://news.cnr.cn/2025zt/szqh/)";
        String fixed = RagRichHtmlToMarkdown.repairBrokenMarkdownLinkSyntax(broken);
        assertTrue(fixed.contains("[![](https://www.cnr.cn/zhuanti/news/w.jpg)](https://news.cnr.cn/2025zt/szqh/)"), fixed);
        assertTrue(fixed.contains("### 学习贯彻党的二十届四中全会精神"), fixed);
        assertFalse(fixed.contains("[### 学习贯彻"), fixed);
    }

    @Test
    void buildRichMarkdown_linkedImageCard_notOverEscaped() {
        String html =
                "<div><h1>热门专题</h1>"
                        + "<a href=\"https://news.cnr.cn/2025zt/szqh/\">"
                        + "<img src=\"https://www.cnr.cn/zhuanti/news/w.jpg\" alt=\"\"/>"
                        + "<h3>学习贯彻党的二十届四中全会精神</h3>"
                        + "<p>中国共产党第二十届中央委员会第四次全体会议。</p>"
                        + "</a></div>";
        var doc = Jsoup.parse(html, "https://news.cnr.cn/");
        String md = RagRichHtmlToMarkdown.buildRichMarkdown("热门专题", doc.body(), doc.baseUri());
        assertTrue(
                md.contains("[![](https://www.cnr.cn/zhuanti/news/w.jpg)](https://news.cnr.cn/2025zt/szqh/)"),
                md);
        assertFalse(md.contains("\\!\\["), md);
        assertFalse(md.contains("\\]\\("), md);
        assertTrue(md.contains("### 学习贯彻党的二十届四中全会精神"), md);
    }
}
