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
 * 一条龙本地规则网页爬取：发现链接 → 按模式清理历史 → 逐 URL 入库。
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
                                categoryId));
    }

    /**
     * @return 是否实际执行了爬取（未获得分布式锁时为 false）
     */
    private boolean runCrawl(
            long tenantId,
            long kbId,
            Long scheduleId,
            String baseUrl,
            RagWebCrawlSyncMode syncMode,
            Integer maxDepth,
            boolean filterCrawled,
            Integer chunkStrategy,
            Long categoryId) {
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
            return false;
        }
        try (var ignored = lock.get()) {
            try {
                RagWebCrawlExtractConfig extractConfig = resolveSiteExtractConfig(tenantId, kbId, scheduleId);
                log.info(
                        "本地规则网页爬取开始 tenantId={} kbId={} scheduleId={} baseUrl={} mode={} filterCrawled={}",
                        tenantId,
                        kbId,
                        scheduleId,
                        baseUrl,
                        syncMode,
                        filterCrawled);
                List<String> raw = linkDiscoveryService.discoverArticleLinks(baseUrl, maxDepth);
                List<String> urls = RagWebCrawlUrlSupport.sanitizeForCrawl(raw, baseUrl);
                if (urls.isEmpty()) {
                    log.warn("本地规则未发现可爬 URL，终止 tenantId={} baseUrl={}", tenantId, baseUrl);
                    return true;
                }
                if (filterCrawled) {
                    Set<String> crawled =
                            scheduleId != null
                                    ? urlItemRepository.activeUrlsForSchedule(tenantId, scheduleId)
                                    : urlItemRepository.activeUrlsForTenant(tenantId);
                    urls = urls.stream().filter(u -> !crawled.contains(u)).toList();
                }
                if (urls.isEmpty()) {
                    log.info("过滤已爬 URL 后无新增，tenantId={} baseUrl={}", tenantId, baseUrl);
                    return true;
                }
                if (syncMode != RagWebCrawlSyncMode.INCREMENTAL) {
                    purgeBeforeFull(tenantId, kbId, scheduleId, baseUrl);
                }
                AtomicInteger ok = new AtomicInteger();
                AtomicInteger fail = new AtomicInteger();
                var semaphore = new java.util.concurrent.Semaphore(WEB_MAX_CONCURRENT);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String url : urls) {
                    futures.add(
                            CompletableFuture.runAsync(
                                    () -> {
                                        try {
                                            semaphore.acquire();
                                            Thread.sleep(WEB_PER_URL_PACING_MS);
                                            FetchedPage fetched = fetchPage(url, extractConfig);
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
                                            semaphore.release();
                                        }
                                    }));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                log.info(
                        "本地规则网页爬取结束 tenantId={} kbId={} scheduleId={} ok={} fail={} total={}",
                        tenantId,
                        kbId,
                        scheduleId,
                        ok.get(),
                        fail.get(),
                        urls.size());
            } catch (Exception e) {
                log.error(
                        "本地规则网页爬取失败 tenantId={} kbId={} scheduleId={} baseUrl={}",
                        tenantId,
                        kbId,
                        scheduleId,
                        baseUrl,
                        e);
            }
            return true;
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

    private FetchedPage fetchPage(String url, RagWebCrawlExtractConfig extractConfig) throws Exception {
        var fetched = RagHttpFetch.get(url);
        String html = new String(fetched.body(), fetched.charset());
        RagHtmlToMarkdown.ParsedPage page = webPageParseService.parse(html, url, extractConfig);
        if (page.markdown().isBlank()) {
            throw new IllegalStateException("empty markdown after crawl");
        }
        return new FetchedPage(RagHtmlToMarkdown.resolveDocumentTitle(page, url), page.markdown());
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
    public String runFromJobPayload(long tenantId, String payloadJson) throws Exception {
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
        boolean ran =
                runCrawl(tenantId, kbId, siteId, baseUrl, mode, maxDepth, filterCrawled, chunkStrategy, categoryId);
        if (ran && siteId != null) {
            markSiteCrawlCompleted(tenantId, siteId);
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("executed", ran);
        out.put("kbId", kbId);
        out.put("baseUrl", baseUrl);
        out.put("syncMode", mode.getCode());
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
