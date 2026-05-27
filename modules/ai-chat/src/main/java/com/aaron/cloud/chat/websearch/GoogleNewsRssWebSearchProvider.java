package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/** Google News RSS（内置固定源，默认 zh-CN）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleNewsRssWebSearchProvider implements WebSearchFixedSourceProvider {

    private static final String HL = "zh-CN";
    private static final int MAX_RESULTS = 8;
    private static final String LABEL = "Google News";
    private final WebSearchProviderLocalCache localCache;
    private final WebSearchFixedSourceHttpService fixedSourceHttp;

    @Override
    public WebSearchFixedSource supports() {
        return WebSearchFixedSource.GOOGLE_NEWS_RSS;
    }

    @Override
    public WebSearchExecutionResult execute(long tenantId, String userQueryPlaintext) throws Exception {
        String q = userQueryPlaintext == null ? "" : userQueryPlaintext.trim();
        if (q.isEmpty()) {
            return new WebSearchExecutionResult(new WebGroundingBundle("", List.of()), null);
        }
        String normalized = WebSearchQueryNormalizer.normalize(q);
        Optional<WebGroundingBundle> cached = localCache.get(tenantId, supports(), normalized);
        if (cached.isPresent()) {
            log.info("[联网搜索] {} 缓存命中，租户 {}", LABEL, tenantId);
            return new WebSearchExecutionResult(cached.get(), null);
        }
        String url = buildRssUrl(q);
        log.info("[联网搜索] {} 外呼 GET {}", LABEL, url);
        Document doc = fixedSourceHttp.jsoupGet(tenantId, url);
        List<WebSearchReference> refs = parseRssItems(doc);
        String summary =
                WebSearchSummarySupport.prefixSummary(
                        LABEL, WebSearchSummarySupport.bulletsFromReferences(refs, MAX_RESULTS));
        WebGroundingBundle bundle = new WebGroundingBundle(summary, refs);
        localCache.put(tenantId, supports(), normalized, bundle);
        return new WebSearchExecutionResult(bundle, null);
    }

    static String buildRssUrl(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://news.google.com/rss/search?q="
                + encoded
                + "&hl="
                + URLEncoder.encode(HL, StandardCharsets.UTF_8)
                + "&gl=CN&ceid=CN:zh-Hans";
    }

    static List<WebSearchReference> parseRssItems(Document doc) {
        List<WebSearchReference> out = new ArrayList<>();
        if (doc == null) {
            return out;
        }
        Elements items = doc.select("item");
        if (items.isEmpty()) {
            items = doc.select("entry");
        }
        for (Element item : items) {
            if (out.size() >= MAX_RESULTS) {
                break;
            }
            String title = textOf(item, "title");
            String link = textOf(item, "link");
            String pubDate = firstNonBlank(textOf(item, "pubDate"), textOf(item, "published"));
            String snippet = firstNonBlank(textOf(item, "description"), textOf(item, "summary"));
            if (snippet.length() > 400) {
                snippet = snippet.substring(0, 400) + "…";
            }
            if (title.isEmpty() && link.isEmpty()) {
                continue;
            }
            out.add(
                    new WebSearchReference(
                            title,
                            link,
                            snippet.isBlank() ? pubDate : snippet,
                            LABEL,
                            null,
                            pubDate.isBlank() ? null : pubDate,
                            null,
                            null));
        }
        return List.copyOf(out);
    }

    private static String textOf(Element parent, String tag) {
        Element el = parent.selectFirst(tag);
        return el == null ? "" : el.text().trim();
    }

    private static String firstNonBlank(String... parts) {
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p.trim();
            }
        }
        return "";
    }
}
