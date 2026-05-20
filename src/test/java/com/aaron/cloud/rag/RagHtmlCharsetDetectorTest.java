package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;

class RagHtmlCharsetDetectorTest {

    @Test
    void resolvesGbkFromMeta() {
        byte[] body = "<html><head><meta charset=\"gb2312\"></head><body>北方网</body></html>"
                .getBytes(Charset.forName("GBK"));
        Charset cs = RagHtmlCharsetDetector.resolve("text/html", body);
        assertEquals("GBK", cs.name());
        String html = new String(body, cs);
        assertTrue(html.contains("北方网"));
    }

    @Test
    void garbledDetector_flagsUtf8MisreadGbk() {
        byte[] gbk = "天津新闻".getBytes(Charset.forName("GBK"));
        String wrong = new String(gbk, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(RagHtmlToMarkdown.looksLikeGarbledText(wrong));
    }
}
