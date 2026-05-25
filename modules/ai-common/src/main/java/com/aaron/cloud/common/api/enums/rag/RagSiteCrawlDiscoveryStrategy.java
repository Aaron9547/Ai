package com.aaron.cloud.common.api.enums.rag;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 绔欑偣鐖彇 discovery.strategies 鐧藉悕鍗曪紙瀛?JSON 瀛楃涓?id锛夈€?*/
@Getter
@RequiredArgsConstructor
public enum RagSiteCrawlDiscoveryStrategy {
    SITEMAP("sitemap"),
    HTML_BFS("html_bfs"),
    LIST_PAGINATION("list_pagination"),
    CMS_VSB("cms_vsb"),
    ARTICLE_HEURISTIC("article_heuristic"),
    RSS_ATOM("rss_atom"),
    JS_RENDER_DISCOVERY("js_render_discovery");

    private final String id;

    public static final Set<String> ALL_IDS =
            Arrays.stream(values()).map(RagSiteCrawlDiscoveryStrategy::getId).collect(Collectors.toUnmodifiableSet());

    public static boolean isAllowed(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        return !t.isEmpty() && ALL_IDS.contains(t);
    }

    public static RagSiteCrawlDiscoveryStrategy fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("discovery strategy id required");
        }
        String t = raw.trim();
        return Arrays.stream(values())
                .filter(s -> s.id.equals(t))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown discovery strategy: " + raw));
    }

    public static java.util.Optional<RagSiteCrawlDiscoveryStrategy> tryFromId(String raw) {
        try {
            return java.util.Optional.of(fromId(raw));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
