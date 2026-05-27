package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class GoogleNewsRssWebSearchProviderTest {

    @Test
    void parseRssItems_extractsTitleLinkAndDate() {
        String xml =
                """
                <rss><channel>
                  <item>
                    <title>示例新闻</title>
                    <link>https://news.example.com/1</link>
                    <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
                    <description>摘要正文</description>
                  </item>
                </channel></rss>
                """;
        List<WebSearchReference> refs =
                GoogleNewsRssWebSearchProvider.parseRssItems(Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser()));
        assertEquals(1, refs.size());
        assertEquals("示例新闻", refs.get(0).title());
        assertEquals("https://news.example.com/1", refs.get(0).url());
        assertTrue(refs.get(0).snippet().contains("摘要"));
    }

    @Test
    void buildRssUrl_usesDefaultLocale() {
        String url = GoogleNewsRssWebSearchProvider.buildRssUrl("AI news");
        assertTrue(url.contains("news.google.com/rss/search"));
        assertTrue(url.contains("hl=zh-CN"));
        assertTrue(url.contains("q=AI"));
    }
}
