package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class BaiduNewsHtmlWebSearchProviderTest {

    @Test
    void parseNewsResults_extractsFromResultOpContainer() {
        String html =
                """
                <div class="result-op c-container">
                  <h3 class="news-title_1YDRn"><a href="https://news.baidu.com/1">标题一</a></h3>
                  <span class="c-color-gray">新华社</span>
                  <span class="c-font-normal">摘要一</span>
                </div>
                """;
        List<WebSearchReference> refs = BaiduNewsHtmlWebSearchProvider.parseNewsResults(Jsoup.parse(html));
        assertEquals(1, refs.size());
        assertEquals("标题一", refs.get(0).title());
        assertEquals("https://news.baidu.com/1", refs.get(0).url());
        assertTrue(refs.get(0).snippet().contains("摘要"));
    }

}
