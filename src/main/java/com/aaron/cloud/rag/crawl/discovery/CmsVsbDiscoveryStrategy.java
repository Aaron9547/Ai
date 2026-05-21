package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagCrawlPageLink;
import com.aaron.cloud.rag.RagVsbListLinkSupport;
import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.fetch.CrawlDiscoveryPageFetcher;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CmsVsbDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final CrawlDiscoveryPageFetcher discoveryPageFetcher;

    @Override
    public String id() {
        return "cms_vsb";
    }

    @Override
    public boolean supports(String strategyId) {
        return "cms_vsb".equalsIgnoreCase(strategyId);
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        try {
            var doc = discoveryPageFetcher.fetchHtmlDocument(ctx.baseUrl(), ctx.policy());
            String root = RagWebCrawlUrlSupport.resolveRootDomain(ctx.baseUrl());
            List<DiscoveredUrl> out = new ArrayList<>();
            for (RagCrawlPageLink link : RagVsbListLinkSupport.extractListArticleLinks(doc, ctx.baseUrl())) {
                if (link.href() == null || link.href().isBlank()) {
                    continue;
                }
                if (!RagWebCrawlUrlSupport.isInRootDomain(link.href(), root)) {
                    continue;
                }
                out.add(new DiscoveredUrl(link.href(), link.title(), CrawlQueueRole.ARTICLE, id()));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
