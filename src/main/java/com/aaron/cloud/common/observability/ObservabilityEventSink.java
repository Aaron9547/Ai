package com.aaron.cloud.common.observability;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.enums.observability.McpTraceSourceScene;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalHitSource;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.common.observability.entity.ObsMcpTraceEvent;
import com.aaron.cloud.common.observability.entity.ObsRagHitEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** 可观测性事件异步旁路落库；失败吞错，不阻断热路径。 */
@Slf4j
@Service
public class ObservabilityEventSink {

    private final PlatformSettingApplicationService platformSettings;
    private final ObsMcpTraceEventRepository mcpTraceEventRepository;
    private final ObsRagHitEventRepository ragHitEventRepository;
    private final ObjectMapper objectMapper;
    private final Executor observabilityExecutor;
    private final AtomicLong droppedEvents = new AtomicLong();

    public ObservabilityEventSink(
            PlatformSettingApplicationService platformSettings,
            ObsMcpTraceEventRepository mcpTraceEventRepository,
            ObsRagHitEventRepository ragHitEventRepository,
            ObjectMapper objectMapper,
            @Qualifier("observabilityExecutor") Executor observabilityExecutor) {
        this.platformSettings = platformSettings;
        this.mcpTraceEventRepository = mcpTraceEventRepository;
        this.ragHitEventRepository = ragHitEventRepository;
        this.objectMapper = objectMapper;
        this.observabilityExecutor = observabilityExecutor;
    }

    public void recordMcpTrace(
            long tenantId,
            String qualifiedName,
            Long serverId,
            JsonNode arguments,
            McpToolInvokeResult result,
            boolean success,
            String errorCode,
            long latencyMs,
            ObservabilityTraceContext.Snapshot ctx) {
        if (!platformSettings.getBoolean(PlatformSettingKey.OBSERVABILITY_ENABLED)) {
            return;
        }
        submit(() -> persistMcpTrace(tenantId, qualifiedName, serverId, arguments, result, success, errorCode, latencyMs, ctx));
    }

    public void recordRagHits(
            long tenantId,
            List<RagCitationHit> hits,
            RagRetrievalMode mode,
            ObservabilityTraceContext.Snapshot ctx) {
        if (!platformSettings.getBoolean(PlatformSettingKey.OBSERVABILITY_ENABLED)
                || hits == null
                || hits.isEmpty()
                || ctx == null) {
            return;
        }
        submit(() -> persistRagHits(tenantId, hits, mode, ctx));
    }

    public void backfillAssistantMessageId(long conversationId, long userMessageId, long assistantMessageId) {
        if (!platformSettings.getBoolean(PlatformSettingKey.OBSERVABILITY_ENABLED)) {
            return;
        }
        submit(() -> {
            try {
                ragHitEventRepository.backfillAssistantMessageId(conversationId, userMessageId, assistantMessageId);
            } catch (Exception e) {
                log.warn(
                        "obs rag hit backfill assistantMessageId failed conv={} userMsg={}",
                        conversationId,
                        userMessageId,
                        e);
            }
        });
    }

    public long droppedEventCount() {
        return droppedEvents.get();
    }

    private void submit(Runnable task) {
        try {
            observabilityExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            long n = droppedEvents.incrementAndGet();
            if (n == 1 || n % 100 == 0) {
                log.warn("observability event queue full, dropped total={}", n);
            }
        }
    }

    private void persistMcpTrace(
            long tenantId,
            String qualifiedName,
            Long serverId,
            JsonNode arguments,
            McpToolInvokeResult result,
            boolean success,
            String errorCode,
            long latencyMs,
            ObservabilityTraceContext.Snapshot ctx) {
        try {
            ObsMcpTraceEvent row = new ObsMcpTraceEvent();
            row.setTraceId(newTraceId());
            if (ctx != null) {
                row.setOrchestrationTraceId(ctx.getOrchestrationTraceId());
                row.setHttpTraceId(firstNonBlank(ctx.getHttpTraceId(), MDC.get("traceId")));
                row.setUserId(ctx.getUserId());
                row.setConversationId(ctx.getConversationId());
                row.setConversationPublicId(ctx.getConversationPublicId());
                row.setSourceScene(ctx.getSourceScene() != null ? ctx.getSourceScene() : McpTraceSourceScene.DIRECT_API);
                row.setLlmRound(ctx.getLlmRound() > 0 ? ctx.getLlmRound() : null);
            } else {
                row.setHttpTraceId(MDC.get("traceId"));
                row.setSourceScene(McpTraceSourceScene.DIRECT_API);
            }
            row.setTenantId(tenantId);
            row.setQualifiedToolName(qualifiedName);
            row.setServerId(serverId);
            row.setSuccess(success);
            row.setErrorCode(errorCode);
            row.setLatencyMs(latencyMs);
            row.setArgumentsJson(ObservabilityJsonSupport.truncateJson(objectMapper, arguments, ObservabilityJsonSupport.DEFAULT_MAX_CHARS));
            if (result != null) {
                row.setResultJson(
                        ObservabilityJsonSupport.truncateJson(objectMapper, result, ObservabilityJsonSupport.DEFAULT_MAX_CHARS));
            }
            row.setCreatedAt(LocalDateTime.now());
            mcpTraceEventRepository.insert(row);
        } catch (Exception e) {
            log.warn("obs mcp trace insert failed tenantId={} tool={}", tenantId, qualifiedName, e);
        }
    }

    private void persistRagHits(
            long tenantId,
            List<RagCitationHit> hits,
            RagRetrievalMode mode,
            ObservabilityTraceContext.Snapshot ctx) {
        try {
            String hitTraceId = newTraceId();
            String httpTraceId = firstNonBlank(ctx.getHttpTraceId(), MDC.get("traceId"));
            LocalDateTime now = LocalDateTime.now();
            List<ObsRagHitEvent> rows = new ArrayList<>();
            int rank = 1;
            for (RagCitationHit h : hits) {
                ObsRagHitEvent row = new ObsRagHitEvent();
                row.setHitTraceId(hitTraceId);
                row.setHttpTraceId(httpTraceId);
                row.setTenantId(tenantId);
                row.setUserId(ctx.getUserId());
                row.setConversationId(ctx.getConversationId());
                row.setUserMessageId(ctx.getUserMessageId());
                row.setKbId(h.kbId());
                row.setDocumentId(h.documentId());
                row.setChunkId(h.chunkId());
                row.setChunkSeq(h.chunkSeq());
                row.setRetrievalMode(mode);
                row.setHitSource(RagRetrievalHitSource.MILVUS);
                row.setQueryText(truncateQuery(ctx.getRagQueryText()));
                row.setRankInBatch(rank++);
                row.setCreatedAt(now);
                rows.add(row);
            }
            ragHitEventRepository.insertBatch(rows);
        } catch (Exception e) {
            log.warn("obs rag hit insert failed tenantId={} conv={}", tenantId, ctx.getConversationId(), e);
        }
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static String truncateQuery(String q) {
        if (q == null) {
            return null;
        }
        return q.length() <= 1024 ? q : q.substring(0, 1024);
    }
}
