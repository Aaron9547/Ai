package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WikipediaRestWebSearchProviderTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void parsePages_readsExtractAndUrl() throws Exception {
        String json =
                """
                {
                  "query": {
                    "pages": {
                      "42": {
                        "pageid": 42,
                        "title": "人工智能",
                        "fullurl": "https://zh.wikipedia.org/wiki/%E4%BA%BA%E5%B7%A5%E6%99%BA%E8%83%BD",
                        "extract": "人工智能是研究智能体的学科。"
                      }
                    }
                  }
                }
                """;
        List<WebSearchReference> refs =
                WikipediaRestWebSearchProvider.parsePages(om.readTree(json), "zh");
        assertEquals(1, refs.size());
        assertEquals("人工智能", refs.get(0).title());
        assertTrue(refs.get(0).url().contains("wikipedia.org"));
        assertTrue(refs.get(0).snippet().contains("智能体"));
    }
}
