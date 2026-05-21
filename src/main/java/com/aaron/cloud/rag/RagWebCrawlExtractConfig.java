package com.aaron.cloud.rag;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 站点级网页正文抽取配置（存 rag_web_crawl_site.extract_config JSON）。 */
@Data
public class RagWebCrawlExtractConfig {

    /** jsoup（默认）或 readability。 */
    private String extractor;

    /** 正文 CSS 选择器，优先于全局 article/main 规则。 */
    private String contentSelector;

    /** 额外剔除区域。 */
    private List<String> excludeSelectors = new ArrayList<>();

    /** 标题 CSS 选择器，优先于 og:title。 */
    private String titleSelector;

    /**
     * 强制不低于某档礼貌（{@code CONSERVATIVE|BALANCED|AGGRESSIVE}）；单站仅允许加严，不可更激进。
     */
    private String presetLock;

    /** 发现层覆盖（仅可加严或减策略，不可提高 QPS）。 */
    private SiteCrawlDiscoveryOverride discovery;

    /** 礼貌层覆盖（仅可加严）。 */
    private SiteCrawlPolitenessOverride politeness;

    @Data
    public static class SiteCrawlDiscoveryOverride {
        private List<String> strategies;
        private Integer maxDepth;
        private JsRenderOverride jsRender;
    }

    @Data
    public static class SiteCrawlPolitenessOverride {
        private Double perHostQps;
        private Integer perHostConcurrency;
        private Integer globalConcurrency;
    }

    @Data
    public static class JsRenderOverride {
        private Boolean enabled;
        private Integer maxPagesPerRun;
        private Integer onlyWhenLinkCountBelow;
    }

    public static RagWebCrawlExtractConfig empty() {
        return new RagWebCrawlExtractConfig();
    }
}
