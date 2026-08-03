package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagCrawlPageLink;
import com.aaron.cloud.rag.RagSiteLinkDiscoveryService;
import com.aaron.cloud.common.api.enums.rag.CrawlQueueRole;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 列表页分页发现：以站点入口为列表页做 harvest（文章 + EXPLORE 入口）。 */
@Component
@RequiredArgsConstructor
public class ListPaginationDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final RagSiteLinkDiscoveryService linkDiscoveryService;

    @Override
    public String id() {
        return "list_pagination";
    }

    @Override
    public boolean supports(String strategyId) {
        return "list_pagination".equalsIgnoreCase(strategyId);
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        List<DiscoveredUrl> out = new ArrayList<>();
        try {
            RagSiteLinkDiscoveryService.ExplorePageHarvest harvest =
                    linkDiscoveryService.harvestExplorePage(ctx.baseUrl(), ctx.baseUrl(), ctx.policy());
            for (RagCrawlPageLink a : harvest.articleLinks()) {
                out.add(new DiscoveredUrl(a.href(), a.title(), CrawlQueueRole.ARTICLE, id()));
            }
            for (String u : harvest.nextExploreUrls()) {
                out.add(new DiscoveredUrl(u, null, CrawlQueueRole.EXPLORE, id()));
            }
        } catch (Exception ignored) {
            // skip
        }
        return out;
    }
}
