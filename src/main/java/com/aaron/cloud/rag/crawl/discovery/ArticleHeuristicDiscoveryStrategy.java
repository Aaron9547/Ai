package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagArticleLinkHeuristics;
import com.aaron.cloud.rag.RagCrawlPageLink;
import com.aaron.cloud.rag.RagSiteLinkDiscoveryService;
import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.fetch.CrawlDiscoveryPageFetcher;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleHeuristicDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final CrawlDiscoveryPageFetcher discoveryPageFetcher;

    @Override
    public String id() {
        return "article_heuristic";
    }

    @Override
    public boolean supports(String strategyId) {
        return "article_heuristic".equalsIgnoreCase(strategyId);
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        try {
            var doc = discoveryPageFetcher.fetchHtmlDocument(ctx.baseUrl(), ctx.policy());
            String root = RagWebCrawlUrlSupport.resolveRootDomain(ctx.baseUrl());
            List<DiscoveredUrl> out = new ArrayList<>();
            for (RagCrawlPageLink link : RagSiteLinkDiscoveryService.extractPageLinks(doc, ctx.baseUrl())) {
                if (!RagArticleLinkHeuristics.looksLikeArticle(link)) {
                    continue;
                }
                String norm = RagWebCrawlUrlSupport.normalizeUrl(link.href());
                if (norm.isEmpty() || !RagWebCrawlUrlSupport.isInRootDomain(norm, root)) {
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
