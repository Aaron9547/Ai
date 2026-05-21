package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.fetch.CrawlDiscoveryPageFetcher;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SitemapDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final CrawlDiscoveryPageFetcher discoveryPageFetcher;

    @Override
    public String id() {
        return "sitemap";
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        int max = ctx.policy().discovery().maxSitemapSeeds();
        String norm = RagWebCrawlUrlSupport.normalizeUrl(ctx.baseUrl());
        String root = RagWebCrawlUrlSupport.resolveRootDomain(norm);
        List<String> seeds = discoveryPageFetcher.fetchSitemapSeeds(norm, root, max, ctx.policy());
        List<DiscoveredUrl> out = new ArrayList<>();
        for (String u : seeds) {
            out.add(new DiscoveredUrl(u, null, CrawlQueueRole.EXPLORE, id()));
        }
        return out;
    }
}
