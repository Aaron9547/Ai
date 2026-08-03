package com.aaron.cloud.chat.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class DuckDuckGoHtmlWebSearchProviderTest {

    @Test
    void parseResults_extractsTitleUrlSnippet() {
        String html =
                """
                <div class="results">
                  <div class="result">
                    <h2 class="result__title"><a class="result__a" href="https://example.com/a">Alpha</a></h2>
                    <a class="result__snippet">Snippet one</a>
                  </div>
                  <div class="result">
                    <h2 class="result__title"><a class="result__a" href="//example.com/b">Beta</a></h2>
                    <div class="result__snippet">Snippet two</div>
                  </div>
                </div>
                """;
        List<WebSearchReference> refs = DuckDuckGoHtmlWebSearchProvider.parseResults(Jsoup.parse(html));
        assertEquals(2, refs.size());
        assertEquals("Alpha", refs.get(0).title());
        assertEquals("https://example.com/a", refs.get(0).url());
        assertEquals("Snippet one", refs.get(0).snippet());
        assertEquals("https://example.com/b", refs.get(1).url());
        assertFalse(refs.get(1).snippet().isBlank());
    }
}
