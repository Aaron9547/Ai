package com.aaron.cloud.rag.crawl.policy;

import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.DiscoveryPolicy;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.ExtractPolicy;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.FetchPolicy;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.IngestPolicy;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.JsRenderPolicy;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.PolitenessPolicy;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy.UrlMergePolicy;
import java.util.List;

/** 三档预设参数真源（与产品方案 §4.2 对齐）。 */
public final class SiteCrawlPresetTemplates {

    private SiteCrawlPresetTemplates() {}

    public static EffectiveSiteCrawlPolicy template(SiteCrawlPreset preset) {
        return switch (preset) {
            case CONSERVATIVE -> conservative();
            case BALANCED -> balanced();
            case AGGRESSIVE -> aggressive();
            case CUSTOM -> balanced();
        };
    }

    public static EffectiveSiteCrawlPolicy conservative() {
        return new EffectiveSiteCrawlPolicy(
                SiteCrawlPreset.CONSERVATIVE,
                new DiscoveryPolicy(
                        List.of("sitemap", "html_bfs", "cms_vsb"),
                        2,
                        800,
                        40,
                        200,
                        600,
                        new JsRenderPolicy(false, 0, 8),
                        new UrlMergePolicy(true, 2)),
                new PolitenessPolicy(true, 0.25, 1, 8, 3000, 5000, 2, 3000, 120_000, 3000),
                new FetchPolicy(3_145_728, 20_000, 2, true, true),
                new ExtractPolicy("jsoup", 48, false),
                new IngestPolicy(80, true, true));
    }

    public static EffectiveSiteCrawlPolicy balanced() {
        return new EffectiveSiteCrawlPolicy(
                SiteCrawlPreset.BALANCED,
                new DiscoveryPolicy(
                        List.of(
                                "sitemap",
                                "html_bfs",
                                "list_pagination",
                                "cms_vsb",
                                "article_heuristic"),
                        2,
                        2000,
                        80,
                        400,
                        2500,
                        new JsRenderPolicy(false, 0, 8),
                        new UrlMergePolicy(true, 1)),
                new PolitenessPolicy(true, 0.5, 2, 16, 1500, 2500, 3, 2000, 60_000, 8000),
                new FetchPolicy(5_242_880, 15_000, 2, true, true),
                new ExtractPolicy("jsoup", 48, false),
                new IngestPolicy(48, true, true));
    }

    public static EffectiveSiteCrawlPolicy aggressive() {
        return new EffectiveSiteCrawlPolicy(
                SiteCrawlPreset.AGGRESSIVE,
                new DiscoveryPolicy(
                        List.of(
                                "sitemap",
                                "html_bfs",
                                "list_pagination",
                                "cms_vsb",
                                "article_heuristic",
                                "rss_atom",
                                "js_render_discovery"),
                        3,
                        2000,
                        80,
                        400,
                        6000,
                        new JsRenderPolicy(true, 15, 12),
                        new UrlMergePolicy(true, 1)),
                new PolitenessPolicy(true, 1.0, 4, 32, 500, 800, 3, 1500, 60_000, 20_000),
                new FetchPolicy(5_242_880, 12_000, 2, true, true),
                new ExtractPolicy("jsoup", 48, true),
                new IngestPolicy(32, true, true));
    }
}
