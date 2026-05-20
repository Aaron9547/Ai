package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagIngestPreviewPickSampleTest {

    @Test
    void pickPreviewSample_prefersInnerPagesOverHome() {
        String home = "https://www.example.edu.cn";
        List<String> urls =
                List.of(
                        home,
                        "https://www.example.edu.cn/news/a.html",
                        "https://www.example.edu.cn/news/b.html",
                        "https://www.example.edu.cn/news/c.html",
                        "https://www.example.edu.cn/news/d.html");
        List<String> sample = RagIngestPreviewApplicationService.pickPreviewSample(urls, 3, home);
        assertEquals(3, sample.size());
        assertFalse(sample.contains(home), "应优先抽样内页而非仅首页");
    }

    @Test
    void pickPreviewSample_returnsAllWhenWithinLimit() {
        List<String> urls = List.of("https://a.com", "https://a.com/p1");
        List<String> sample = RagIngestPreviewApplicationService.pickPreviewSample(urls, 3, "https://a.com");
        assertEquals(2, sample.size());
    }
}
