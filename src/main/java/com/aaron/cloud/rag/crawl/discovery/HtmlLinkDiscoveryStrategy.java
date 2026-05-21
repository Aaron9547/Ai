package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagCrawlPageLink;
import com.aaron.cloud.rag.RagSiteLinkDiscoveryService;
import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** html_bfs / list_pagination / cms_vsb / article_heuristic 合并为既有 BFS 发现。 */
@Component
@RequiredArgsConstructor
public class HtmlLinkDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final RagSiteLinkDiscoveryService linkDiscoveryService;

    @Override
    public String id() {
        return "html_bfs";
    }

    @Override
    public boolean supports(String strategyId) {
        return "html_bfs".equalsIgnoreCase(strategyId);
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        int depth =
                ctx.maxDepth() != null
                        ? ctx.maxDepth()
                        : ctx.policy().discovery().maxDepthDefault();
        List<RagCrawlPageLink> links =
                linkDiscoveryService.discoverSiteArticles(ctx.baseUrl(), depth, ctx.policy());
        List<DiscoveredUrl> out = new ArrayList<>();
        for (RagCrawlPageLink link : links) {
            if (link.href() == null || link.href().isBlank()) {
                continue;
            }
            out.add(
                    new DiscoveredUrl(
                            link.href(),
                            link.title(),
                            CrawlQueueRole.ARTICLE,
                            "html_bfs"));
        }
        return out;
    }
}
