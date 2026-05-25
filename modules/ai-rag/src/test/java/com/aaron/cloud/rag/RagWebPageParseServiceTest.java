package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.config.properties.AiRagProperties;
import org.junit.jupiter.api.Test;

class RagWebPageParseServiceTest {

    @Test
    void parse_vsbArticle_includesImageMarkdown() {
        String html =
                "<html><head><title>张壹-商学院</title></head><body>"
                        + "<div id=\"vsb_newscontent\"><div id=\"vsb_content_4\">"
                        + "<p class=\"vsbcontent_img\">"
                        + "<img src=\"/__local/photo.jpg\" width=\"165\" alt=\"肖像\">"
                        + "</p>"
                        + "<p>张壹，中共党员，商学院学生。</p>"
                        + "</div></div></body></html>";
        var props = new AiRagProperties();
        var svc = new RagWebPageParseService(props);
        var page = svc.parse(html, "https://sxy.zua.edu.cn/info/1157/6633.htm", null, "张壹");
        assertTrue(page.title().contains("张壹"), "title=" + page.title());
        assertTrue(page.markdown().contains("!["), "md=" + page.markdown());
        assertTrue(page.markdown().contains("photo.jpg"), page.markdown());
    }
}
