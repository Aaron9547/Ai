package com.aaron.cloud.rag.crawl.pipeline;

import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.entity.CrawlUrlQueue;
import com.aaron.cloud.rag.RagHtmlToMarkdown;
import com.aaron.cloud.rag.RagIngestOrchestrationService;
import com.aaron.cloud.rag.RagWebCrawlExtractConfig;
import com.aaron.cloud.rag.RagWebPageParseService;
import com.aaron.cloud.common.api.enums.rag.CrawlQueueStatus;
import com.aaron.cloud.rag.crawl.fetch.HttpFetcher;
import com.aaron.cloud.rag.crawl.fetch.HttpFetcherException;
import com.aaron.cloud.rag.crawl.fetch.PolitenessGate;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlPagePipeline {

    private final PolitenessGate politenessGate;
    private final HttpFetcher httpFetcher;
    private final RagWebPageParseService webPageParseService;
    private final QualityGate qualityGate;
    private final RagIngestOrchestrationService ingestOrchestrationService;
    private final CrawlUrlQueueRepository crawlUrlQueueRepository;

    public record PageOutcome(CrawlQueueStatus status, Long documentId, String errorCode, String etag, String lastModified) {}

    public PageOutcome process(
            long tenantId,
            long kbId,
            String url,
            String listTitleHint,
            RagWebCrawlExtractConfig extractConfig,
            EffectiveSiteCrawlPolicy policy,
            Integer chunkStrategy,
            Long categoryId,
            CrawlUrlQueue queueRow)
            throws Exception {
        int retryMax = Math.max(0, policy.politeness().retryMax());
        int attempt = 0;
        while (true) {
            try {
                return processOnce(
                        tenantId,
                        kbId,
                        url,
                        listTitleHint,
                        extractConfig,
                        policy,
                        chunkStrategy,
                        categoryId,
                        queueRow);
            } catch (HttpFetcherException e) {
                if (e.retryable()) {
                    politenessGate.onRateLimited(url);
                }
                if (attempt >= retryMax) {
                    return new PageOutcome(
                            CrawlQueueStatus.FAILED,
                            null,
                            "HTTP_" + e.statusCode(),
                            null,
                            null);
                }
                bumpRetry(queueRow);
                sleepBackoff(policy, attempt);
                attempt++;
            } catch (PolitenessGate.RobotsDisallowedException e) {
                return new PageOutcome(CrawlQueueStatus.SKIPPED_ROBOTS, null, "ROBOTS_DISALLOW", null, null);
            } catch (Exception e) {
                if (attempt >= retryMax) {
                    return new PageOutcome(CrawlQueueStatus.FAILED, null, "FETCH_ERROR", null, null);
                }
                bumpRetry(queueRow);
                sleepBackoff(policy, attempt);
                attempt++;
            }
        }
    }

    private PageOutcome processOnce(
            long tenantId,
            long kbId,
            String url,
            String listTitleHint,
            RagWebCrawlExtractConfig extractConfig,
            EffectiveSiteCrawlPolicy policy,
            Integer chunkStrategy,
            Long categoryId,
            CrawlUrlQueue queueRow)
            throws Exception {
        politenessGate.acquire(url, policy.politeness());
        try {
            var fetched =
                    httpFetcher.fetch(
                            url,
                            policy.fetch(),
                            siteReferer(url),
                            queueRow != null ? queueRow.getEtag() : null,
                            queueRow != null ? queueRow.getLastModified() : null);
            if (fetched.statusCode() == 304) {
                return new PageOutcome(
                        CrawlQueueStatus.SKIPPED_NOT_MODIFIED,
                        queueRow != null ? queueRow.getDocumentId() : null,
                        null,
                        fetched.etag(),
                        fetched.lastModified());
            }
            String html = new String(fetched.body(), fetched.charset());
            String parseUrl = fetched.finalUrl() != null ? fetched.finalUrl() : url;
            RagHtmlToMarkdown.ParsedPage page =
                    webPageParseService.parse(html, parseUrl, extractConfig, listTitleHint);
            var qg = qualityGate.check(page.markdown(), html, policy.ingest());
            if (!qg.pass()) {
                return new PageOutcome(
                        CrawlQueueStatus.FAILED, null, qg.errorCode(), fetched.etag(), fetched.lastModified());
            }
            var pr =
                    ingestOrchestrationService.ingestWebMarkdownSync(
                            tenantId,
                            kbId,
                            url,
                            page.markdown(),
                            page.title(),
                            chunkStrategy,
                            categoryId);
            return new PageOutcome(
                    CrawlQueueStatus.DONE, pr.documentId(), null, fetched.etag(), fetched.lastModified());
        } finally {
            politenessGate.release();
        }
    }

    private void bumpRetry(CrawlUrlQueue queueRow) {
        if (queueRow != null && queueRow.getId() != null) {
            crawlUrlQueueRepository.bumpRetryCount(queueRow.getId());
        }
    }

    private static void sleepBackoff(EffectiveSiteCrawlPolicy policy, int attempt) throws InterruptedException {
        long base = Math.max(1L, policy.politeness().backoffBaseMs());
        long max = Math.max(base, policy.politeness().backoffMaxMs());
        long delay = Math.min(max, base * (1L << Math.min(attempt, 10)));
        Thread.sleep(delay);
    }

    private static String siteReferer(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            if (u.getHost() == null) {
                return null;
            }
            return u.getScheme() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return null;
        }
    }
}
