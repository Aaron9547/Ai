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

/** 百度新闻搜索页（内置固定源，国内直连）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaiduNewsHtmlWebSearchProvider implements WebSearchFixedSourceProvider {

    private static final int MAX_RESULTS = 8;
    private static final String LABEL = "百度新闻";
    private final WebSearchProviderLocalCache localCache;
    private final WebSearchFixedSourceHttpService fixedSourceHttp;

    @Override
    public WebSearchFixedSource supports() {
        return WebSearchFixedSource.BAIDU_NEWS_HTML;
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
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String url = "https://www.baidu.com/s?tn=news&word=" + encoded;
        log.info("[联网搜索] {} 外呼 GET {}", LABEL, url);
        Document doc = fixedSourceHttp.jsoupGet(tenantId, url);
        List<WebSearchReference> refs = parseNewsResults(doc);
        String summary =
                WebSearchSummarySupport.prefixSummary(
                        LABEL, WebSearchSummarySupport.bulletsFromReferences(refs, MAX_RESULTS));
        WebGroundingBundle bundle = new WebGroundingBundle(summary, refs);
        localCache.put(tenantId, supports(), normalized, bundle);
        return new WebSearchExecutionResult(bundle, null);
    }

    static List<WebSearchReference> parseNewsResults(Document doc) {
        List<WebSearchReference> out = new ArrayList<>();
        if (doc == null) {
            return out;
        }
        Elements blocks = doc.select("div.result-op.c-container");
        if (blocks.isEmpty()) {
            blocks = doc.select("div.result.c-container, div[class*=result]");
        }
        for (Element block : blocks) {
            if (out.size() >= MAX_RESULTS) {
                break;
            }
            WebSearchReference ref = mapBlock(block);
            if (ref != null) {
                out.add(ref);
            }
        }
        if (out.isEmpty()) {
            for (Element h3 : doc.select("h3 a[href]")) {
                if (out.size() >= MAX_RESULTS) {
                    break;
                }
                String title = h3.text().trim();
                String href = h3.attr("href").trim();
                if (!title.isEmpty()) {
                    out.add(new WebSearchReference(title, href, "", LABEL, null, null, null, null));
                }
            }
        }
        return List.copyOf(out);
    }

    private static WebSearchReference mapBlock(Element block) {
        Element titleLink = block.selectFirst("h3.news-title_1YDRn a, h3 a, a[data-click]");
        if (titleLink == null) {
            titleLink = block.selectFirst("a");
        }
        String title = titleLink == null ? "" : titleLink.text().trim();
        String link = titleLink == null ? "" : titleLink.attr("href").trim();
        Element summaryEl = block.selectFirst("span.c-font-normal, .c-span-last, .c-abstract");
        String snippet = summaryEl == null ? "" : summaryEl.text().trim();
        Element sourceEl = block.selectFirst("span.c-color-gray, .news-source");
        String source = sourceEl == null ? "" : sourceEl.text().trim();
        if (!source.isBlank() && !snippet.isBlank()) {
            snippet = source + " · " + snippet;
        } else if (!source.isBlank()) {
            snippet = source;
        }
        if (title.isEmpty() && snippet.isEmpty()) {
            return null;
        }
        return new WebSearchReference(title, link, snippet, LABEL, null, null, null, null);
    }
}
