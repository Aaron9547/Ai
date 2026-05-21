package com.aaron.cloud.rag.crawl;

import com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.rag.CrawlRunRepository;
import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.LnkRagKbDocumentRepository;
import com.aaron.cloud.common.rag.RagDocumentRepository;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.common.rag.RagWebCrawlUrlItemRepository;
import com.aaron.cloud.common.rag.entity.CrawlRun;
import com.aaron.cloud.common.rag.entity.CrawlUrlQueue;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.entity.RagWebCrawlUrlItem;
import com.aaron.cloud.common.redis.RedisDistributedLockService;
import com.aaron.cloud.common.task.LongRunningTaskProgressReporter;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.rag.RagDocumentChunkPurgeService;
import com.aaron.cloud.rag.RagWebCrawlExtractConfig;
import com.aaron.cloud.rag.RagWebCrawlExtractConfigSupport;
import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.discovery.CrawlDiscoveryOrchestrator;
import com.aaron.cloud.rag.crawl.discovery.CrawlExploreQueueConsumer;
import com.aaron.cloud.rag.crawl.discovery.DiscoveryResult;
import com.aaron.cloud.rag.crawl.discovery.UrlMergeService;
import com.aaron.cloud.rag.crawl.fetch.PolitenessGate;
import com.aaron.cloud.rag.crawl.pipeline.CrawlPagePipeline;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.aaron.cloud.rag.crawl.policy.SiteCrawlPolicyResolver;
import com.aaron.cloud.scheduled.TenantScheduledTaskLockKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlRunOrchestrator {

    private final SiteCrawlPolicyResolver policyResolver;
    private final PolitenessGate politenessGate;
    private final CrawlDiscoveryOrchestrator discoveryOrchestrator;
    private final CrawlExploreQueueConsumer exploreQueueConsumer;
    private final CrawlUrlQueueWriter crawlUrlQueueWriter;
    private final CrawlPagePipeline crawlPagePipeline;
    private final CrawlRunRepository crawlRunRepository;
    private final CrawlUrlQueueRepository crawlUrlQueueRepository;
    private final RagWebCrawlUrlItemRepository urlItemRepository;
    private final RagWebCrawlSiteRepository webCrawlSiteRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final LnkRagKbDocumentRepository lnkRagKbDocumentRepository;
    private final RagDocumentChunkPurgeService ragDocumentChunkPurgeService;
    private final RagWebCrawlExtractConfigSupport extractConfigSupport;
    private final RedisDistributedLockService distributedLockService;
    private final AiRagProperties aiRagProperties;
    private final ObjectMapper objectMapper;
    @Qualifier(CrawlRunExecutorConfig.CRAWL_RUN_EXECUTOR)
    private final Executor crawlRunExecutor;

    public static final class RunStats {
        public boolean executed;
        public int discovered;
        public int toCrawl;
        public int ok;
        public int skipped;
        public int fail;
        public String summary;
        public Long runId;
        public String preset;
        public String policySummary;
        public Map<String, Integer> discoveryByStrategy = new LinkedHashMap<>();
        public Map<String, Integer> failedByCode = new LinkedHashMap<>();
    }

    public RunStats run(
            long tenantId,
            long kbId,
            Long siteId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId,
            LongRunningTaskProgressReporter progress) {
        var stats = new RunStats();
        LongRunningTaskProgressReporter reporter =
                progress == null ? LongRunningTaskProgressSupport.noop() : progress;
        String lockKey =
                siteId != null
                        ? TenantScheduledTaskLockKeys.siteRun(tenantId, siteId)
                        : TenantScheduledTaskLockKeys.siteOneShot(tenantId, baseUrl, categoryId);
        Duration lockTtl = Duration.ofSeconds(aiRagProperties.getSiteCrawl().getCrawlLockTtlSeconds());
        Optional<RedisDistributedLockService.DistributedLockHandle> lock =
                distributedLockService.tryAcquire(lockKey, lockTtl);
        if (lock.isEmpty()) {
            log.info("站点爬取跳过（持锁） tenantId={} baseUrl={}", tenantId, baseUrl);
            return stats;
        }
        try (var ignored = lock.get()) {
            stats.executed = true;
            RagWebCrawlExtractConfig siteConfig = resolveSiteExtractConfig(tenantId, kbId, siteId);
            EffectiveSiteCrawlPolicy policy = policyResolver.resolve(tenantId, siteConfig);
            politenessGate.configure(policy.politeness());
            stats.preset = policy.preset().name();
            stats.policySummary = policy.policySummaryLine();
            log.info(
                    "站点爬取开始 tenantId={} kbId={} preset={} policy={}",
                    tenantId,
                    kbId,
                    stats.preset,
                    stats.policySummary);
            CrawlRun run = startRun(tenantId, kbId, siteId, baseUrl, syncMode, policy);
            stats.runId = run.getId();
            try {
                executeRunBody(
                        tenantId,
                        kbId,
                        siteId,
                        baseUrl,
                        syncMode,
                        maxDepth,
                        filterCrawled,
                        chunkStrategy,
                        categoryId,
                        run,
                        siteConfig,
                        policy,
                        stats,
                        reporter,
                        true);
            } catch (Exception e) {
                stats.summary = "爬取异常：" + e.getMessage();
                run.setStatus("FAILED");
                crawlRunRepository.updateById(run);
                reporter.report("FAILED", stats.summary, null, null, null);
                log.error("站点爬取失败 tenantId={} baseUrl={}", tenantId, baseUrl, e);
            }
            return stats;
        }
    }

    /** 同 run 续跑：仅消费队列中 PENDING 的 ARTICLE（不重新发现）。 */
    public RunStats resumePendingArticles(
            long tenantId,
            long kbId,
            long runId,
            Integer chunkStrategy,
            Long categoryId,
            LongRunningTaskProgressReporter progress) {
        CrawlRun run =
                crawlRunRepository
                        .findById(tenantId, runId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "crawl run not found"));
        if (!kbIdEquals(run.getKbId(), kbId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kbId mismatch");
        }
        var stats = new RunStats();
        stats.executed = true;
        stats.runId = runId;
        RagWebCrawlExtractConfig siteConfig = resolveSiteExtractConfig(tenantId, kbId, run.getSiteId());
        EffectiveSiteCrawlPolicy policy = policyResolver.resolve(tenantId, siteConfig);
        politenessGate.configure(policy.politeness());
        stats.preset = run.getPreset() != null ? run.getPreset() : policy.preset().name();
        stats.policySummary =
                run.getPolicySummary() != null ? run.getPolicySummary() : policy.policySummaryLine();
        LongRunningTaskProgressReporter reporter =
                progress == null ? LongRunningTaskProgressSupport.noop() : progress;
        run.setStatus("RUNNING");
        crawlRunRepository.updateById(run);
        try {
            executeRunBody(
                    tenantId,
                    kbId,
                    run.getSiteId(),
                    run.getBaseUrl(),
                    RagWebCrawlSyncMode.fromCode(run.getSyncMode()),
                    null,
                    false,
                    chunkStrategy,
                    categoryId,
                    run,
                    siteConfig,
                    policy,
                    stats,
                    reporter,
                    false);
        } catch (Exception e) {
            stats.summary = "续跑异常：" + e.getMessage();
            run.setStatus("FAILED");
            crawlRunRepository.updateById(run);
            reporter.report("FAILED", stats.summary, null, null, null);
        }
        return stats;
    }

    /** 将本 run 的 FAILED 行重置为 PENDING 后同 run 续爬。 */
    public RunStats retryFailedArticles(
            long tenantId,
            long kbId,
            long runId,
            Integer chunkStrategy,
            Long categoryId,
            LongRunningTaskProgressReporter progress) {
        CrawlRun run =
                crawlRunRepository
                        .findById(tenantId, runId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "crawl run not found"));
        if (!kbIdEquals(run.getKbId(), kbId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kbId mismatch");
        }
        int reset = crawlUrlQueueRepository.resetFailedToPending(runId);
        log.info("crawl run retry-failed runId={} resetRows={}", runId, reset);
        if (reset == 0) {
            var stats = new RunStats();
            stats.executed = true;
            stats.runId = runId;
            stats.summary = "无 FAILED 队列项可重试";
            return stats;
        }
        return resumePendingArticles(tenantId, kbId, runId, chunkStrategy, categoryId, progress);
    }

    private void executeRunBody(
            long tenantId,
            long kbId,
            Long siteId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId,
            CrawlRun run,
            RagWebCrawlExtractConfig siteConfig,
            EffectiveSiteCrawlPolicy policy,
            RunStats stats,
            LongRunningTaskProgressReporter reporter,
            boolean fullDiscovery)
            throws Exception {
        String normBase = RagWebCrawlUrlSupport.normalizeUrl(baseUrl);
        Map<String, String> listTitles = new LinkedHashMap<>();

        if (fullDiscovery) {
            reporter.report("LOCKED", "已获取爬取锁", 5, null, null);
            DiscoveryResult discovery = discoveryOrchestrator.discover(baseUrl, maxDepth, policy);
            stats.discoveryByStrategy.putAll(discovery.discoveryByStrategy());
            reporter.report(
                    "DISCOVER",
                    "多策略发现完成",
                    10,
                    null,
                    null,
                    Map.of("discoveryByStrategy", stats.discoveryByStrategy));
            crawlUrlQueueWriter.upsertMerged(run.getId(), tenantId, discovery.merged());
            for (UrlMergeService.MergedUrl m : discovery.merged()) {
                if (m.role() == CrawlQueueRole.ARTICLE
                        && m.title() != null
                        && !m.title().isBlank()) {
                    listTitles.putIfAbsent(RagWebCrawlUrlSupport.normalizeUrl(m.url()), m.title().trim());
                }
            }
            reporter.report("EXPLORE", "消费 EXPLORE 队列续发现", 12, null, null);
            exploreQueueConsumer.consumeExploreQueue(run.getId(), tenantId, normBase, maxDepth, policy);
        }

        stats.discovered = crawlUrlQueueRepository.countPendingArticles(run.getId());
        if (stats.discovered == 0 && fullDiscovery) {
            stats.summary = "未发现可爬 URL";
            finishRun(run, stats);
            reporter.report("DONE", stats.summary, 100, 0, 0);
            return;
        }

        if (filterCrawled) {
            Set<String> crawled =
                    siteId != null
                            ? urlItemRepository.activeUrlsForSchedule(tenantId, siteId)
                            : urlItemRepository.activeUrlsForTenant(tenantId);
            if (!crawled.isEmpty()) {
                Set<String> crawledNorms = new HashSet<>();
                for (String u : crawled) {
                    String norm = RagWebCrawlUrlSupport.normalizeUrl(u);
                    if (!norm.isEmpty()) {
                        crawledNorms.add(norm);
                    }
                }
                crawlUrlQueueRepository.markArticlesSkipped(run.getId(), crawledNorms, "ALREADY_CRAWLED");
            }
        }

        int maxArticles = policy.discovery().maxArticlesPerRun();
        if (maxArticles > 0) {
            int trimmed = crawlUrlQueueRepository.capPendingArticles(run.getId(), maxArticles);
            if (trimmed > 0) {
                log.info("crawl run capped articles runId={} max={} trimmed={}", run.getId(), maxArticles, trimmed);
            }
        }

        stats.toCrawl = crawlUrlQueueRepository.countPendingArticles(run.getId());
        if (stats.toCrawl == 0) {
            stats.summary = fullDiscovery ? "过滤已爬 URL 后无新增" : "无待爬队列项";
            finishRun(run, stats);
            reporter.report("DONE", stats.summary, 100, 0, 0);
            return;
        }

        if (fullDiscovery && syncMode != RagWebCrawlSyncMode.INCREMENTAL) {
            reporter.report("PURGE", "全量模式：清理历史文档", 15, null, null);
            purgeBeforeFull(tenantId, kbId, siteId, baseUrl);
        }

        crawlPendingArticles(
                tenantId,
                kbId,
                siteId,
                run,
                siteConfig,
                policy,
                chunkStrategy,
                categoryId,
                listTitles,
                stats,
                reporter);
        stats.summary =
                "爬取结束：入库 "
                        + stats.ok
                        + "，跳过 "
                        + stats.skipped
                        + "，失败 "
                        + stats.fail;
        finishRun(run, stats);
        reporter.report("DONE", stats.summary, 100, stats.ok, stats.toCrawl);
    }

    private void crawlPendingArticles(
            long tenantId,
            long kbId,
            Long siteId,
            CrawlRun run,
            RagWebCrawlExtractConfig siteConfig,
            EffectiveSiteCrawlPolicy policy,
            Integer chunkStrategy,
            Long categoryId,
            Map<String, String> listTitles,
            RunStats stats,
            LongRunningTaskProgressReporter reporter)
            throws Exception {
        final int total = stats.toCrawl;
        reporter.report("CRAWL", "队列消费 ARTICLE " + total + " 条", 20, 0, total);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        Semaphore sem = new Semaphore(policy.politeness().globalConcurrency());
        int batchSize = Math.max(10, policy.politeness().globalConcurrency() * 4);

        while (true) {
            List<CrawlUrlQueue> batch = crawlUrlQueueRepository.listPendingArticles(run.getId(), batchSize);
            if (batch.isEmpty()) {
                break;
            }
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (CrawlUrlQueue row : batch) {
                futures.add(
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        sem.acquire();
                                        row.setStatus(CrawlQueueStatus.IN_PROGRESS.name());
                                        crawlUrlQueueRepository.updateById(row);
                                        String hint =
                                                listTitles.get(
                                                        row.getUrlNorm() != null
                                                                ? row.getUrlNorm()
                                                                : RagWebCrawlUrlSupport.normalizeUrl(
                                                                        row.getUrl()));
                                        var outcome =
                                                crawlPagePipeline.process(
                                                        tenantId,
                                                        kbId,
                                                        row.getUrl(),
                                                        hint,
                                                        siteConfig,
                                                        policy,
                                                        chunkStrategy,
                                                        categoryId,
                                                        row);
                                        if (outcome.status() == CrawlQueueStatus.DONE) {
                                            recordUrlItem(
                                                    tenantId, siteId, row.getUrl(), outcome.documentId());
                                            ok.incrementAndGet();
                                        } else if (outcome.status() == CrawlQueueStatus.SKIPPED_NOT_MODIFIED
                                                || outcome.status() == CrawlQueueStatus.SKIPPED_FILTERED
                                                || outcome.status() == CrawlQueueStatus.SKIPPED_ROBOTS) {
                                            skipped.incrementAndGet();
                                        } else {
                                            fail.incrementAndGet();
                                            bump(stats.failedByCode, outcome.errorCode());
                                        }
                                        updateQueueRow(row, outcome);
                                    } catch (Exception e) {
                                        fail.incrementAndGet();
                                        bump(stats.failedByCode, "FETCH_ERROR");
                                        row.setStatus(CrawlQueueStatus.FAILED.name());
                                        row.setErrorCode("FETCH_ERROR");
                                        crawlUrlQueueRepository.updateById(row);
                                        log.warn("单 URL 失败 url={} err={}", row.getUrl(), e.toString());
                                    } finally {
                                        int d = done.incrementAndGet();
                                        Integer pct = total > 0 ? 20 + (d * 75 / total) : null;
                                        reporter.report(
                                                "CRAWL",
                                                "爬取入库 " + Math.min(d, total) + "/" + total,
                                                pct,
                                                Math.min(d, total),
                                                total);
                                        sem.release();
                                    }
                                },
                                crawlRunExecutor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        stats.ok = ok.get();
        stats.skipped = skipped.get();
        stats.fail = fail.get();
    }

    private static void bump(Map<String, Integer> m, String code) {
        String k = code == null ? "UNKNOWN" : code;
        m.merge(k, 1, Integer::sum);
    }

    private static boolean kbIdEquals(Long runKbId, long expected) {
        return runKbId != null && runKbId == expected;
    }

    private CrawlRun startRun(
            long tenantId,
            long kbId,
            Long siteId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            EffectiveSiteCrawlPolicy policy) {
        CrawlRun run = new CrawlRun();
        run.setTenantId(tenantId);
        run.setKbId(kbId);
        run.setSiteId(siteId);
        run.setBaseUrl(baseUrl);
        run.setSyncMode(syncMode.getCode());
        run.setPreset(policy.preset().name());
        run.setPolicySummary(policy.policySummaryLine());
        run.setStatus("RUNNING");
        crawlRunRepository.insert(run);
        return run;
    }

    private void finishRun(CrawlRun run, RunStats stats) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("discovered", stats.discovered);
        node.put("toCrawl", stats.toCrawl);
        node.put("ok", stats.ok);
        node.put("skipped", stats.skipped);
        node.put("fail", stats.fail);
        node.put("preset", stats.preset);
        node.put("policySummary", stats.policySummary);
        ObjectNode fbc = objectMapper.createObjectNode();
        stats.failedByCode.forEach(fbc::put);
        node.set("failedByCode", fbc);
        ObjectNode dbs = objectMapper.createObjectNode();
        stats.discoveryByStrategy.forEach(dbs::put);
        node.set("discoveryByStrategy", dbs);
        run.setStatsJson(objectMapper.writeValueAsString(node));
        run.setStatus("DONE");
        crawlRunRepository.updateById(run);
    }

    private void updateQueueRow(CrawlUrlQueue row, CrawlPagePipeline.PageOutcome outcome) {
        row.setStatus(outcome.status().name());
        row.setErrorCode(outcome.errorCode());
        row.setDocumentId(outcome.documentId());
        row.setEtag(outcome.etag());
        row.setLastModified(outcome.lastModified());
        crawlUrlQueueRepository.updateById(row);
    }

    private RagWebCrawlExtractConfig resolveSiteExtractConfig(long tenantId, long kbId, Long siteId) {
        if (siteId == null) {
            return RagWebCrawlExtractConfig.empty();
        }
        return webCrawlSiteRepository
                .findById(tenantId, kbId, siteId)
                .map(s -> extractConfigSupport.fromJson(s.getExtractConfig()))
                .orElse(RagWebCrawlExtractConfig.empty());
    }

    private void recordUrlItem(long tenantId, Long scheduleId, String url, long documentId) {
        if (scheduleId == null) {
            return;
        }
        var item = new RagWebCrawlUrlItem();
        item.setTenantId(tenantId);
        item.setScheduleId(scheduleId);
        item.setUrl(url);
        item.setDocumentId(documentId);
        item.setDeleted(0);
        urlItemRepository.insert(item);
    }

    private void purgeBeforeFull(long tenantId, long kbId, Long scheduleId, String baseUrl) {
        if (scheduleId == null) {
            log.info("一次性全量无 siteId，跳过历史清理 baseUrl={}", baseUrl);
            return;
        }
        List<RagWebCrawlUrlItem> items = urlItemRepository.listAllBySchedule(tenantId, scheduleId);
        Set<Long> docIds = new HashSet<>();
        for (RagWebCrawlUrlItem item : items) {
            if (item.getDocumentId() != null && item.getDeleted() != null && item.getDeleted() == 0) {
                docIds.add(item.getDocumentId());
            }
        }
        for (Long docId : docIds) {
            try {
                deleteKbDocument(tenantId, kbId, docId);
            } catch (Exception e) {
                log.warn("全量清理失败 docId={}", docId, e);
            }
        }
        urlItemRepository.markDeletedBySchedule(tenantId, scheduleId);
    }

    private void deleteKbDocument(long tenantId, long kbId, long documentId) {
        RagDocument doc = ragDocumentRepository.findByIdAndTenant(documentId, tenantId);
        if (doc == null || doc.getDeleted() != null && doc.getDeleted() == 1) {
            return;
        }
        ragDocumentChunkPurgeService.purgeAllChunksForDocument(tenantId, kbId, documentId);
        urlItemRepository.markDeletedByDocumentIds(tenantId, List.of(documentId));
        lnkRagKbDocumentRepository.deleteLink(kbId, documentId);
        doc.setDeleted(1);
        doc.setDeletedAt(BeijingTime.nowLocal());
        ragDocumentRepository.updateById(doc);
    }
}
