package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.util.List;

public interface CrawlDiscoveryStrategy {

    /** 主策略 id（写入 sources_json）。 */
    String id();

    /** 是否处理配置中的该 strategy 键。 */
    default boolean supports(String strategyId) {
        return id().equalsIgnoreCase(strategyId);
    }

    List<DiscoveredUrl> discover(DiscoveryContext ctx);

    record DiscoveryContext(
            String baseUrl,
            Integer maxDepth,
            EffectiveSiteCrawlPolicy policy) {}
}
