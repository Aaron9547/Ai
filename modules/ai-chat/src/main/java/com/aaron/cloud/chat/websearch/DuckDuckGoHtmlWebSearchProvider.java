package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;

import java.net.URI;
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

/** DuckDuckGo HTML 结果页（内置固定源，非 {@code llm_model} 配置）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuckDuckGoHtmlWebSearchProvider implements WebSearchFixedSourceProvider {

    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final int MAX_RESULTS = 8;
    private static final String LABEL = "DuckDuckGo";
    private final WebSearchProviderLocalCache localCache;
    private final WebSearchFixedSourceHttpService fixedSourceHttp;

    @Override
    public WebSearchFixedSource supports() {
        return WebSearchFixedSource.DUCKDUCKGO_HTML;
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
        String getUrl =
                SEARCH_URL
                        + "?q="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8);
        log.info("[联网搜索] {} 外呼 GET {}", LABEL, getUrl);
        Document doc = fixedSourceHttp.jsoupGet(tenantId, getUrl);
        List<WebSearchReference> refs = parseResults(doc);
        String summary =
                WebSearchSummarySupport.prefixSummary(
                        LABEL, WebSearchSummarySupport.bulletsFromReferences(refs, MAX_RESULTS));
        WebGroundingBundle bundle = new WebGroundingBundle(summary, refs);
        localCache.put(tenantId, supports(), normalized, bundle);
        return new WebSearchExecutionResult(bundle, null);
    }

    static List<WebSearchReference> parseResults(Document doc) {
        List<WebSearchReference> out = new ArrayList<>();
        if (doc == null) {
            return out;
        }
        Elements results = doc.select("div.result");
        if (results.isEmpty()) {
            results = doc.select(".web-result, .result--web");
        }
        for (Element block : results) {
            if (out.size() >= MAX_RESULTS) {
                break;
            }
            Element titleLink = block.selectFirst("a.result__a");
            if (titleLink == null) {
                titleLink = block.selectFirst("h2 a");
            }
            String title = titleLink == null ? "" : titleLink.text().trim();
            String href = titleLink == null ? "" : normalizeDuckDuckGoUrl(titleLink.attr("href").trim());
            Element snipEl = block.selectFirst(".result__snippet, .result__body");
            String snippet = snipEl == null ? "" : snipEl.text().trim();
            if (title.isEmpty() && href.isEmpty() && snippet.isEmpty()) {
                continue;
            }
            out.add(new WebSearchReference(title, href, snippet, LABEL, null, null, null, null));
        }
        return List.copyOf(out);
    }

    private static String normalizeDuckDuckGoUrl(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        String u = href.trim();
        if (u.startsWith("//")) {
            return "https:" + u;
        }
        if (u.startsWith("/l/?") || u.contains("uddg=")) {
            try {
                URI uri = URI.create(u.startsWith("http") ? u : "https://duckduckgo.com" + u);
                String query = uri.getRawQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("uddg=")) {
                            return java.net.URLDecoder.decode(
                                    part.substring(5), StandardCharsets.UTF_8);
                        }
                    }
                }
            } catch (Exception ignored) {
                return u;
            }
        }
        return u;
    }
}
