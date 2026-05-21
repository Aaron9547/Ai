package com.aaron.cloud.rag.crawl.policy;

import java.util.ArrayList;
import java.util.List;

/** 合并后的站点爬取有效策略（租户 + 单站 + yml 兜底）。 */
public record EffectiveSiteCrawlPolicy(
        SiteCrawlPreset preset,
        DiscoveryPolicy discovery,
        PolitenessPolicy politeness,
        FetchPolicy fetch,
        ExtractPolicy extract,
        IngestPolicy ingest) {

    public record DiscoveryPolicy(
            List<String> strategies,
            int maxDepthDefault,
            int maxExplorePages,
            int maxPaginationPerList,
            int maxSitemapSeeds,
            /** 单次 run 最多保留的 ARTICLE 队列项；0 表示不限制。 */
            int maxArticlesPerRun,
            JsRenderPolicy jsRender,
            UrlMergePolicy urlMerge) {}

    public record JsRenderPolicy(boolean enabled, int maxPagesPerRun, int onlyWhenLinkCountBelow) {}

    public record UrlMergePolicy(boolean boostMultiSource, int minSourcesForBoost) {}

    public record PolitenessPolicy(
            boolean respectRobots,
            double perHostQps,
            int perHostConcurrency,
            int globalConcurrency,
            int jitterMsMin,
            int jitterMsMax,
            int retryMax,
            long backoffBaseMs,
            long backoffMaxMs,
            int dailyMaxRequestsPerHost) {}

    public record FetchPolicy(
            int maxBodyBytes,
            int timeoutMs,
            int metaRefreshMaxHops,
            boolean conditionalRequest,
            boolean sharedHttpClient) {}

    public record ExtractPolicy(
            String defaultExtractor, int readabilityFallbackMinLen, boolean readabilityFallbackEnabled) {}

    public record IngestPolicy(
            int minMarkdownChars, boolean rejectGarbled, boolean rejectRedirectShellOnly) {}

    public String policySummaryLine() {
        return preset
                + " · QPS="
                + politeness.perHostQps()
                + " · host并发="
                + politeness.perHostConcurrency()
                + " · 全局="
                + politeness.globalConcurrency()
                + " · 策略="
                + String.join(",", discovery.strategies());
    }

    public static List<String> copyStrategies(List<String> in) {
        return in == null ? new ArrayList<>() : new ArrayList<>(in);
    }
}
