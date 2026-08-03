package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.chat.websearch.cache.WebSearchQueryNormalizer;
import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** 维基百科 MediaWiki API（内置固定源，默认 zh）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WikipediaRestWebSearchProvider implements WebSearchFixedSourceProvider {

    private static final String WIKI_LANG = "zh";
    private static final int MAX_RESULTS = 5;
    private static final int EXTRACT_MAX_CHARS = 600;
    private static final String LABEL = "Wikipedia";

    private final ObjectMapper objectMapper;
    private final WebSearchProviderLocalCache localCache;
    private final WebSearchFixedSourceHttpService fixedSourceHttp;

    @Override
    public WebSearchFixedSource supports() {
        return WebSearchFixedSource.WIKIPEDIA_REST;
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
        String apiUrl =
                "https://"
                        + WIKI_LANG
                        + ".wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8)
                        + "&gsrlimit="
                        + MAX_RESULTS
                        + "&prop=extracts|info&exintro=1&explaintext=1&inprop=url&format=json&utf8=1";
        log.info("[联网搜索] {} 外呼 GET {}", LABEL, apiUrl);
        String body =
                fixedSourceHttp
                        .restClient(tenantId)
                        .get()
                        .uri(apiUrl)
                        .header("User-Agent", WebSearchFixedSourceHttp.USER_AGENT)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(String.class);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("维基百科 API 返回空响应");
        }
        JsonNode root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        List<WebSearchReference> refs = parsePages(root, WIKI_LANG);
        String summary =
                WebSearchSummarySupport.prefixSummary(
                        LABEL, WebSearchSummarySupport.bulletsFromReferences(refs, MAX_RESULTS));
        WebGroundingBundle bundle = new WebGroundingBundle(summary, refs);
        localCache.put(tenantId, supports(), normalized, bundle);
        return new WebSearchExecutionResult(bundle, null);
    }

    static List<WebSearchReference> parsePages(JsonNode root, String lang) {
        List<WebSearchReference> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        JsonNode pages = root.path("query").path("pages");
        if (!pages.isObject()) {
            return out;
        }
        Iterator<String> ids = pages.fieldNames();
        while (ids.hasNext()) {
            JsonNode p = pages.path(ids.next());
            if (!p.isObject() || p.path("missing").asBoolean(false)) {
                continue;
            }
            String title = p.path("title").asText("").trim();
            String url = p.path("fullurl").asText("").trim();
            if (url.isBlank() && !title.isEmpty()) {
                url =
                        "https://"
                                + lang
                                + ".wikipedia.org/wiki/"
                                + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
            }
            String extract = p.path("extract").asText("").trim();
            if (extract.length() > EXTRACT_MAX_CHARS) {
                extract = extract.substring(0, EXTRACT_MAX_CHARS) + "…";
            }
            if (title.isEmpty() && extract.isEmpty()) {
                continue;
            }
            out.add(new WebSearchReference(title, url, extract, LABEL, null, null, null, null));
        }
        return List.copyOf(out);
    }
}
