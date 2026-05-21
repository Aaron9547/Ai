package com.aaron.cloud.rag.crawl.policy;

import java.util.Set;

/** 允许的发现策略 id 白名单。 */
public final class SiteCrawlStrategyIds {

    public static final Set<String> ALL =
            Set.of(
                    "sitemap",
                    "html_bfs",
                    "list_pagination",
                    "cms_vsb",
                    "article_heuristic",
                    "rss_atom",
                    "js_render_discovery");

    private SiteCrawlStrategyIds() {}
}
