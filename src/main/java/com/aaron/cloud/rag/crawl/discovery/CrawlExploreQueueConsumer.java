package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagCrawlPageLink;
import com.aaron.cloud.rag.RagSiteLinkDiscoveryService;
import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.CrawlQueueStatus;
import com.aaron.cloud.rag.crawl.CrawlUrlQueueWriter;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.entity.CrawlUrlQueue;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 消费 {@code crawl_url_queue} 中 EXPLORE 行，继续发现并 upsert 新 URL。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlExploreQueueConsumer {

    private final CrawlUrlQueueRepository crawlUrlQueueRepository;
    private final RagSiteLinkDiscoveryService linkDiscoveryService;
    private final UrlMergeService urlMergeService;
    private final CrawlUrlQueueWriter crawlUrlQueueWriter;

    public int consumeExploreQueue(
            long runId,
            long tenantId,
            String normBase,
            Integer maxDepth,
            EffectiveSiteCrawlPolicy policy) {
        int maxPages = policy.discovery().maxExplorePages();
        int depthLimit = RagWebCrawlUrlSupport.normalizeDepth(
                maxDepth != null ? maxDepth : policy.discovery().maxDepthDefault());
        int processed = 0;
        int rounds = 0;
        while (rounds++ < depthLimit + 2) {
            List<CrawlUrlQueue> pending =
                    crawlUrlQueueRepository.listPendingExplore(runId, Math.min(50, maxPages));
            if (pending.isEmpty()) {
                break;
            }
            List<DiscoveredUrl> batch = new ArrayList<>();
            for (CrawlUrlQueue row : pending) {
                if (processed >= maxPages) {
                    break;
                }
                row.setStatus(CrawlQueueStatus.IN_PROGRESS.name());
                crawlUrlQueueRepository.updateById(row);
                try {
                    RagSiteLinkDiscoveryService.ExplorePageHarvest harvest =
                            linkDiscoveryService.harvestExplorePage(row.getUrl(), normBase, policy);
                    for (RagCrawlPageLink article : harvest.articleLinks()) {
                        batch.add(
                                new DiscoveredUrl(
                                        article.href(),
                                        article.title(),
                                        CrawlQueueRole.ARTICLE,
                                        "explore"));
                    }
                    for (String next : harvest.nextExploreUrls()) {
                        batch.add(new DiscoveredUrl(next, null, CrawlQueueRole.EXPLORE, "explore"));
                    }
                    row.setStatus(CrawlQueueStatus.DONE.name());
                } catch (Exception e) {
                    row.setStatus(CrawlQueueStatus.FAILED.name());
                    row.setErrorCode("EXPLORE_FAILED");
                    log.debug("EXPLORE 失败 url={} err={}", row.getUrl(), e.toString());
                }
                crawlUrlQueueRepository.updateById(row);
                processed++;
            }
            if (!batch.isEmpty()) {
                var merged = urlMergeService.merge(batch, policy.discovery());
                crawlUrlQueueWriter.upsertMerged(runId, tenantId, merged);
            }
            if (processed >= maxPages) {
                break;
            }
        }
        return processed;
    }
}
