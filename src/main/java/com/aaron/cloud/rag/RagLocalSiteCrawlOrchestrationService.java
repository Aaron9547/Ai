package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagWebCrawlSyncMode;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.rag.crawl.CrawlRunOrchestrator;
import com.aaron.cloud.common.task.LongRunningTaskProgressReporter;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final CrawlRunOrchestrator crawlRunOrchestrator;
    private final RagWebCrawlSiteRepository webCrawlSiteRepository;
    private final ObjectMapper objectMapper;
    @Qualifier(com.aaron.cloud.rag.crawl.CrawlRunExecutorConfig.CRAWL_RUN_EXECUTOR)
    private final Executor crawlRunExecutor;

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
                                null),
                crawlRunExecutor);
    }

    private static final class SiteCrawlRunStats {
        boolean executed;
        int discovered;
        int toCrawl;
        int ok;
        int skipped;
        int fail;
        String summary;
        String preset;
        String policySummary;
        Long runId;
        java.util.Map<String, Integer> failedByCode = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> discoveryByStrategy = new java.util.LinkedHashMap<>();
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
        LongRunningTaskProgressReporter progressReporter =
                progress == null ? LongRunningTaskProgressSupport.noop() : progress;
        CrawlRunOrchestrator.RunStats orchestrated =
                crawlRunOrchestrator.run(
                        tenantId,
                        kbId,
                        scheduleId,
                        baseUrl,
                        syncMode,
                        maxDepth,
                        filterCrawled,
                        chunkStrategy,
                        categoryId,
                        progressReporter);
        stats.executed = orchestrated.executed;
        stats.discovered = orchestrated.discovered;
        stats.toCrawl = orchestrated.toCrawl;
        stats.ok = orchestrated.ok;
        stats.skipped = orchestrated.skipped;
        stats.fail = orchestrated.fail;
        stats.summary = orchestrated.summary;
        stats.preset = orchestrated.preset;
        stats.policySummary = orchestrated.policySummary;
        stats.runId = orchestrated.runId;
        if (orchestrated.failedByCode != null) {
            stats.failedByCode.putAll(orchestrated.failedByCode);
        }
        if (orchestrated.discoveryByStrategy != null) {
            stats.discoveryByStrategy.putAll(orchestrated.discoveryByStrategy);
        }
        return stats;
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
        out.put("skippedCount", stats.skipped);
        out.put("failCount", stats.fail);
        if (stats.summary != null) {
            out.put("summary", stats.summary);
        }
        if (stats.preset != null) {
            out.put("preset", stats.preset);
        }
        if (stats.policySummary != null) {
            out.put("policySummary", stats.policySummary);
        }
        if (stats.runId != null) {
            out.put("runId", stats.runId);
        }
        if (!stats.failedByCode.isEmpty()) {
            var fbc = objectMapper.createObjectNode();
            stats.failedByCode.forEach(fbc::put);
            out.set("failedByCode", fbc);
        }
        if (!stats.discoveryByStrategy.isEmpty()) {
            var dbs = objectMapper.createObjectNode();
            stats.discoveryByStrategy.forEach(dbs::put);
            out.set("discoveryByStrategy", dbs);
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
