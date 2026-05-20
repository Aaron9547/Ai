package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.rag.LnkRagDocumentChunkRepository;
import com.aaron.cloud.common.rag.LnkRagKbDocumentRepository;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagDocumentRepository;
import com.aaron.cloud.common.rag.RagWebCrawlUrlItemRepository;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.scheduled.TenantScheduledTaskLockKeys;
import com.aaron.cloud.common.rag.entity.LnkRagDocumentChunk;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.entity.RagWebCrawlUrlItem;
import com.aaron.cloud.common.redis.RedisDistributedLockService;
import com.aaron.cloud.common.task.LongRunningTaskProgressReporter;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.aaron.cloud.common.time.BeijingTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站点爬取编排：按层级 BFS 发现同域 URL → 按同步模式清理历史 → 每个 URL 独立入库为一篇文档。
 *
 * <p>参考 ly-ai-application {@code LocalArticleLinksWebCrawlService} 与 {@code crawlArticleLinksLocal}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagLocalSiteCrawlOrchestrationService {

    private static final int WEB_MAX_CONCURRENT = 4;
    private static final long WEB_PER_URL_PACING_MS = 500L;

    private final RagSiteLinkDiscoveryService linkDiscoveryService;
    private final RagIngestOrchestrationService ingestOrchestrationService;
    private final RagWebCrawlUrlItemRepository urlItemRepository;
    private final RagWebCrawlSiteRepository webCrawlSiteRepository;
    private final RagDocumentRepository ragDocumentRepository;
    private final RagChunkRepository ragChunkRepository;
    private final LnkRagKbDocumentRepository lnkRagKbDocumentRepository;
    private final LnkRagDocumentChunkRepository lnkRagDocumentChunkRepository;
    private final VectorStorePort vectorStorePort;
    private final RagDocumentChunkPurgeService ragDocumentChunkPurgeService;
    private final ObjectMapper objectMapper;
    private final RedisDistributedLockService distributedLockService;
    private final AiRagProperties aiRagProperties;
    private final RagWebPageParseService webPageParseService;
    private final RagWebCrawlExtractConfigSupport extractConfigSupport;

    /** 一次性提交（无 schedule 记录），异步执行。 */
    public void submitOneShotAsync(
            long tenantId,
            long kbId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId) {
        CompletableFuture.runAsync(
                () ->
                        runCrawl(
                                tenantId,
                                kbId,
                                null,
                                baseUrl,
                                syncMode,
                                maxDepth,
                                filterCrawled,
                                chunkStrategy,
                                categoryId,
                                null));
    }

    private static final class SiteCrawlRunStats {
        boolean executed;
        int discovered;
        int toCrawl;
        int ok;
        int fail;
        String summary;
    }

    /**
     * @return 执行统计（未获得分布式锁时 {@code executed=false}）
     */
    private SiteCrawlRunStats runCrawl(
            long tenantId,
            long kbId,
            Long scheduleId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId,
            LongRunningTaskProgressReporter progress) {
        var stats = new SiteCrawlRunStats();
        final LongRunningTaskProgressReporter progressReporter =
                progress == null ? LongRunningTaskProgressSupport.noop() : progress;
        String lockKey =
                scheduleId != null
                        ? TenantScheduledTaskLockKeys.siteRun(tenantId, scheduleId)
                        : TenantScheduledTaskLockKeys.siteOneShot(tenantId, baseUrl, categoryId);
        Duration lockTtl = Duration.ofSeconds(aiRagProperties.getSiteCrawl().getCrawlLockTtlSeconds());
        Optional<RedisDistributedLockService.DistributedLockHandle> lock =
                distributedLockService.tryAcquire(lockKey, lockTtl);
        if (lock.isEmpty()) {
            log.info(
                    "本地规则网页爬取跳过（其它实例持锁） tenantId={} kbId={} scheduleId={} baseUrl={} lockKey={}",
                    tenantId,
                    kbId,
                    scheduleId,
                    baseUrl,
                    lockKey);
            return stats;
        }
        try (var ignored = lock.get()) {
            stats.executed = true;
            try {
                progressReporter.report("LOCKED", "已获取爬取锁，准备发现链接", 5, null, null);
                RagWebCrawlExtractConfig extractConfig = resolveSiteExtractConfig(tenantId, kbId, scheduleId);
                log.info(
                        "本地规则网页爬取开始 tenantId={} kbId={} scheduleId={} baseUrl={} mode={} filterCrawled={}",
                        tenantId,
                        kbId,
                        scheduleId,
                        baseUrl,
                        syncMode,
                        filterCrawled);
                progressReporter.report("DISCOVER", "正在按层级发现站点链接", 10, null, null);
                List<RagCrawlPageLink> rawArticles =
                        linkDiscoveryService.discoverSiteArticles(baseUrl, maxDepth);
                java.util.Map<String, String> listTitles = new java.util.LinkedHashMap<>();
                List<String> raw = new ArrayList<>();
                for (RagCrawlPageLink link : rawArticles) {
                    if (link.href() != null && !link.href().isBlank()) {
                        raw.add(link.href());
                        if (link.title() != null && !link.title().isBlank()) {
                            listTitles.putIfAbsent(
                                    RagWebCrawlUrlSupport.normalizeUrl(link.href()), link.title().trim());
                        }
                    }
                }
                List<String> discovered = RagWebCrawlUrlSupport.sanitizeForCrawl(raw, baseUrl);
                stats.discovered = discovered.size();
                if (discovered.isEmpty()) {
                    log.warn("本地规则未发现可爬 URL，终止 tenantId={} baseUrl={}", tenantId, baseUrl);
                    stats.summary = "未发现可爬 URL";
                    progressReporter.report("DONE", stats.summary, 100, 0, 0);
                    return stats;
                }
                if (filterCrawled) {
                    Set<String> crawled =
                            scheduleId != null
                                    ? urlItemRepository.activeUrlsForSchedule(tenantId, scheduleId)
                                    : urlItemRepository.activeUrlsForTenant(tenantId);
                    discovered = discovered.stream().filter(u -> !crawled.contains(u)).toList();
                }
                final List<String> urls = discovered;
                if (urls.isEmpty()) {
                    log.info("过滤已爬 URL 后无新增，tenantId={} baseUrl={}", tenantId, baseUrl);
                    stats.summary = "过滤已爬 URL 后无新增";
                    progressReporter.report("DONE", stats.summary, 100, 0, 0);
                    return stats;
                }
                stats.toCrawl = urls.size();
                if (syncMode != RagWebCrawlSyncMode.INCREMENTAL) {
                    progressReporter.report("PURGE", "全量模式：清理历史文档", 15, null, null);
                    purgeBeforeFull(tenantId, kbId, scheduleId, baseUrl);
                }
                final int totalUrls = urls.size();
                progressReporter.report("CRAWL", "开始逐 URL 入库", 20, 0, totalUrls);
                AtomicInteger ok = new AtomicInteger();
                AtomicInteger fail = new AtomicInteger();
                AtomicInteger done = new AtomicInteger();
                var semaphore = new java.util.concurrent.Semaphore(WEB_MAX_CONCURRENT);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String url : urls) {
                    futures.add(
                            CompletableFuture.runAsync(
                                    () -> {
                                        try {
                                            semaphore.acquire();
                                            Thread.sleep(WEB_PER_URL_PACING_MS);
                                            String listHint =
                                                    listTitles.get(
                                                            RagWebCrawlUrlSupport.normalizeUrl(url));
                                            FetchedPage fetched =
                                                    fetchPage(url, extractConfig, listHint);
                                            var pr =
                                                    ingestOrchestrationService.ingestWebMarkdownSync(
                                                            tenantId,
                                                            kbId,
                                                            url,
                                                            fetched.markdown(),
                                                            fetched.title(),
                                                            chunkStrategy,
                                                            categoryId);
                                            recordUrlItem(tenantId, scheduleId, url, pr.documentId());
                                            ok.incrementAndGet();
                                        } catch (Exception e) {
                                            fail.incrementAndGet();
                                            log.warn("单 URL 入库失败 url={} err={}", url, e.toString());
                                        } finally {
                                            int d = done.incrementAndGet();
                                            Integer pct =
                                                    totalUrls > 0 ? 20 + (d * 75 / totalUrls) : null;
                                            progressReporter.report(
                                                    "CRAWL",
                                                    "爬取入库 " + d + "/" + totalUrls,
                                                    pct,
                                                    d,
                                                    totalUrls);
                                            semaphore.release();
                                        }
                                    }));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                stats.ok = ok.get();
                stats.fail = fail.get();
                stats.summary = "爬取结束：成功 " + stats.ok + "，失败 " + stats.fail;
                progressReporter.report("DONE", stats.summary, 100, stats.ok, totalUrls);
                log.info(
                        "本地规则网页爬取结束 tenantId={} kbId={} scheduleId={} ok={} fail={} total={}",
                        tenantId,
                        kbId,
                        scheduleId,
                        stats.ok,
                        stats.fail,
                        urls.size());
            } catch (Exception e) {
                stats.summary = "爬取异常：" + e.getMessage();
                progressReporter.report("FAILED", stats.summary, null, null, null);
                log.error(
                        "本地规则网页爬取失败 tenantId={} kbId={} scheduleId={} baseUrl={}",
                        tenantId,
                        kbId,
                        scheduleId,
                        baseUrl,
                        e);
            }
            return stats;
        }
    }

    private void purgeBeforeFull(long tenantId, long kbId, Long scheduleId, String baseUrl) {
        if (scheduleId != null) {
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
                    log.warn("全量清理删除文档失败 docId={} err={}", docId, e.toString());
                }
            }
            urlItemRepository.markDeletedBySchedule(tenantId, scheduleId);
            return;
        }
        log.info("一次性全量爬取无 scheduleId，跳过历史文档批量删除 baseUrl={}", baseUrl);
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

    private record FetchedPage(String title, String markdown) {}

    private FetchedPage fetchPage(String url, RagWebCrawlExtractConfig extractConfig, String listTitleHint)
            throws Exception {
        var fetched = RagHttpFetch.get(url, 0, siteReferer(url));
        String html = RagHttpFetch.decodeHtml(fetched);
        String parseUrl = fetched.finalUrl() != null ? fetched.finalUrl() : url;
        RagHtmlToMarkdown.ParsedPage page =
                webPageParseService.parse(html, parseUrl, extractConfig, listTitleHint);
        if (page.markdown().isBlank()) {
            throw new IllegalStateException("empty markdown after crawl");
        }
        return new FetchedPage(page.title(), page.markdown());
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

    private RagWebCrawlExtractConfig resolveSiteExtractConfig(long tenantId, long kbId, Long siteId) {
        if (siteId == null) {
            return RagWebCrawlExtractConfig.empty();
        }
        return webCrawlSiteRepository
                .findById(tenantId, kbId, siteId)
                .map(s -> extractConfigSupport.fromJson(s.getExtractConfig()))
                .orElse(RagWebCrawlExtractConfig.empty());
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

    /** 供 job 任务执行：payload 含 kbId、baseUrl 等。 */
    public String runFromJobPayload(
            long tenantId, String payloadJson, LongRunningTaskProgressReporter progress)
            throws Exception {
        final LongRunningTaskProgressReporter progressReporter =
                progress == null ? LongRunningTaskProgressSupport.noop() : progress;
        var root = objectMapper.readTree(payloadJson == null ? "{}" : payloadJson);
        long kbId = root.get("kbId").asLong();
        String baseUrl = root.get("baseUrl").asText();
        RagWebCrawlSyncMode mode =
                RagWebCrawlSyncMode.fromCode(
                        root.has("syncMode") ? root.get("syncMode").asText() : RagWebCrawlSyncMode.FULL.getCode());
        boolean filterCrawled = !root.has("filterCrawled") || root.get("filterCrawled").asBoolean(true);
        Integer maxDepth = root.has("maxDepth") ? root.get("maxDepth").asInt() : null;
        Integer chunkStrategy = root.has("chunkStrategy") && !root.get("chunkStrategy").isNull()
                ? root.get("chunkStrategy").asInt()
                : null;
        Long categoryId = root.has("categoryId") && !root.get("categoryId").isNull()
                ? root.get("categoryId").asLong()
                : null;
        Long siteId = null;
        if (root.has("siteId") && !root.get("siteId").isNull()) {
            siteId = root.get("siteId").asLong();
        } else if (root.has("scheduleId") && !root.get("scheduleId").isNull()) {
            siteId = root.get("scheduleId").asLong();
        } else if (root.has("scheduledTaskId") && !root.get("scheduledTaskId").isNull()) {
            siteId = root.get("scheduledTaskId").asLong();
        }
        SiteCrawlRunStats stats =
                runCrawl(
                        tenantId,
                        kbId,
                        siteId,
                        baseUrl,
                        mode,
                        maxDepth,
                        filterCrawled,
                        chunkStrategy,
                        categoryId,
                        progressReporter);
        if (stats.executed && siteId != null) {
            markSiteCrawlCompleted(tenantId, siteId);
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("executed", stats.executed);
        out.put("kbId", kbId);
        out.put("baseUrl", baseUrl);
        out.put("syncMode", mode.getCode());
        out.put("discoveredUrls", stats.discovered);
        out.put("toCrawlUrls", stats.toCrawl);
        out.put("successCount", stats.ok);
        out.put("failCount", stats.fail);
        if (stats.summary != null) {
            out.put("summary", stats.summary);
        }
        return objectMapper.writeValueAsString(out);
    }

    private void markSiteCrawlCompleted(long tenantId, long siteId) {
        webCrawlSiteRepository
                .findByIdForTenant(tenantId, siteId)
                .ifPresent(
                        site -> {
                            if (site.getSyncMode() == RagWebCrawlSyncMode.FIRST_FULL_THEN_INCREMENTAL
                                    && site.getFirstRunDone() != null
                                    && site.getFirstRunDone() == 0) {
                                site.setFirstRunDone(1);
                            }
                            webCrawlSiteRepository.updateById(site);
                        });
    }
}
