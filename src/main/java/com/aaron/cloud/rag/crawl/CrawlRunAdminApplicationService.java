package com.aaron.cloud.rag.crawl;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.rag.CrawlRunRepository;
import com.aaron.cloud.common.rag.CrawlUrlQueueRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.CrawlRun;
import com.aaron.cloud.common.task.LongRunningTaskProgressSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CrawlRunAdminApplicationService {

    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final CrawlRunRepository crawlRunRepository;
    private final CrawlUrlQueueRepository crawlUrlQueueRepository;
    private final CrawlRunOrchestrator crawlRunOrchestrator;
    private final ObjectMapper objectMapper;

    public record CrawlRunSummaryView(
            long runId,
            long kbId,
            Long siteId,
            String baseUrl,
            String syncMode,
            String status,
            String preset,
            String createdAt) {}

    public record CrawlRunDetailView(
            long runId,
            long kbId,
            Long siteId,
            String baseUrl,
            String syncMode,
            String status,
            String preset,
            String policySummary,
            String statsJson,
            Map<String, Long> queueByStatus,
            Map<String, Integer> failedByCode,
            Map<String, Integer> discoveryByStrategy,
            int ok,
            int skipped,
            int fail) {}

    public List<CrawlRunSummaryView> listRuns(long kbId, int limit, Long siteId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        List<CrawlRunSummaryView> out = new ArrayList<>();
        for (CrawlRun run : crawlRunRepository.listByKb(tenantId, kbId, limit, siteId)) {
            out.add(
                    new CrawlRunSummaryView(
                            run.getId(),
                            run.getKbId(),
                            run.getSiteId(),
                            run.getBaseUrl(),
                            run.getSyncMode(),
                            run.getStatus(),
                            run.getPreset(),
                            run.getCreatedAt() != null ? run.getCreatedAt().toString() : null));
        }
        return out;
    }

    public CrawlRunDetailView getRun(long kbId, long runId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        CrawlRun run =
                crawlRunRepository
                        .findById(tenantId, runId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "crawl run not found"));
        if (run.getKbId() == null || run.getKbId() != kbId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "crawl run not found");
        }
        Map<String, Long> byStatus = crawlUrlQueueRepository.countByStatus(runId);
        Map<String, Integer> failedByCode = new LinkedHashMap<>();
        Map<String, Integer> discoveryByStrategy = new LinkedHashMap<>();
        int[] counts = parseStatsCounts(run.getStatsJson(), failedByCode, discoveryByStrategy);
        return new CrawlRunDetailView(
                run.getId(),
                run.getKbId(),
                run.getSiteId(),
                run.getBaseUrl(),
                run.getSyncMode(),
                run.getStatus(),
                run.getPreset(),
                run.getPolicySummary(),
                run.getStatsJson(),
                byStatus,
                failedByCode,
                discoveryByStrategy,
                counts[0],
                counts[1],
                counts[2]);
    }

    public Map<String, Object> retryFailed(long kbId, long runId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        CrawlRunOrchestrator.RunStats stats =
                crawlRunOrchestrator.retryFailedArticles(tenantId, kbId, runId, null, null, null);
        return statsMap(stats, runId);
    }

    public Map<String, Object> resumePending(long kbId, long runId) {
        long tenantId = TenantContextHolder.require().getTenantId();
        requireKb(kbId, tenantId);
        CrawlRunOrchestrator.RunStats stats =
                crawlRunOrchestrator.resumePendingArticles(
                        tenantId, kbId, runId, null, null, LongRunningTaskProgressSupport.noop());
        return statsMap(stats, runId);
    }

    private static Map<String, Object> statsMap(CrawlRunOrchestrator.RunStats stats, long runId) {
        return Map.of(
                "runId",
                stats.runId != null ? stats.runId : runId,
                "executed",
                stats.executed,
                "ok",
                stats.ok,
                "skipped",
                stats.skipped,
                "fail",
                stats.fail,
                "summary",
                stats.summary != null ? stats.summary : "");
    }

    private int[] parseStatsCounts(
            String statsJson,
            Map<String, Integer> failedByCode,
            Map<String, Integer> discoveryByStrategy) {
        int ok = 0;
        int skipped = 0;
        int fail = 0;
        if (statsJson == null || statsJson.isBlank()) {
            return new int[] {ok, skipped, fail};
        }
        try {
            JsonNode root = objectMapper.readTree(statsJson);
            JsonNode fbc = root.get("failedByCode");
            if (fbc != null && fbc.isObject()) {
                fbc.fields()
                        .forEachRemaining(
                                e -> failedByCode.put(e.getKey(), e.getValue().asInt(0)));
            }
            JsonNode dbs = root.get("discoveryByStrategy");
            if (dbs != null && dbs.isObject()) {
                dbs.fields()
                        .forEachRemaining(
                                e ->
                                        discoveryByStrategy.put(
                                                e.getKey(), e.getValue().asInt(0)));
            }
            if (root.has("ok")) {
                ok = root.get("ok").asInt(0);
            } else if (root.has("successCount")) {
                ok = root.get("successCount").asInt(0);
            }
            if (root.has("skipped")) {
                skipped = root.get("skipped").asInt(0);
            } else if (root.has("skippedCount")) {
                skipped = root.get("skippedCount").asInt(0);
            }
            if (root.has("fail")) {
                fail = root.get("fail").asInt(0);
            } else if (root.has("failCount")) {
                fail = root.get("failCount").asInt(0);
            }
        } catch (Exception ignored) {
            /* 展示层容错 */
        }
        return new int[] {ok, skipped, fail};
    }

    private void requireKb(long kbId, long tenantId) {
        if (ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found");
        }
    }
}
