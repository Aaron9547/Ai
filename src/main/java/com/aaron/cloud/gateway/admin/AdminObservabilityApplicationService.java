package com.aaron.cloud.gateway.admin;

import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.observability.ObsMcpTraceEventRepository;
import com.aaron.cloud.common.observability.ObsRagHitEventRepository;
import com.aaron.cloud.common.observability.ObservabilityAdminDisplaySupport;
import com.aaron.cloud.common.observability.entity.ObsMcpTraceEvent;
import com.aaron.cloud.common.observability.entity.ObsRagHitEvent;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminObservabilityApplicationService {

    private final ObsMcpTraceEventRepository mcpTraceEventRepository;
    private final ObsRagHitEventRepository ragHitEventRepository;
    private final ObservabilityAdminDisplaySupport displaySupport;
    private final ChatConversationRepository chatConversationRepository;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;

    public Page<ObsMcpTraceEvent> pageMcpTraces(
            Long filterTenantId,
            long page,
            long size,
            Long conversationId,
            String conversationKeyword,
            String toolName,
            Boolean success,
            int days) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 90)));
        List<Long> conversationIds = resolveConversationIds(tid, conversationId, conversationKeyword);
        if (conversationIds != null && conversationIds.isEmpty()) {
            return emptyPage(page, size);
        }
        Page<ObsMcpTraceEvent> result =
                mcpTraceEventRepository.pageForAdmin(
                        tid, page, size, conversationId, conversationIds, toolName, success, since);
        displaySupport.enrichMcpTraces(result.getRecords());
        return result;
    }

    public ObsMcpTraceEvent getMcpTrace(String traceId) {
        ObsMcpTraceEvent row = mcpTraceEventRepository.findByTraceId(traceId);
        if (row != null) {
            displaySupport.enrichMcpTraces(List.of(row));
        }
        return row;
    }

    public Page<ObsRagHitEvent> pageRagHits(
            Long filterTenantId,
            long page,
            long size,
            Long conversationId,
            String conversationKeyword,
            String queryKeyword,
            Long kbId,
            String kbNameKeyword,
            Long chunkId,
            int days) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 90)));
        List<Long> conversationIds = resolveConversationIds(tid, conversationId, conversationKeyword);
        if (conversationIds != null && conversationIds.isEmpty()) {
            return emptyPage(page, size);
        }
        List<Long> kbIds = resolveKbIds(tid, kbId, kbNameKeyword);
        if (kbIds != null && kbIds.isEmpty()) {
            return emptyPage(page, size);
        }
        Page<ObsRagHitEvent> result =
                ragHitEventRepository.pageForAdmin(
                        tid, page, size, conversationId, conversationIds, kbId, kbIds, chunkId, queryKeyword, since);
        displaySupport.enrichRagHits(result.getRecords());
        return result;
    }

    public List<ObsRagHitEvent> listRagHitsByTraceId(String hitTraceId) {
        List<ObsRagHitEvent> rows = ragHitEventRepository.listByHitTraceId(hitTraceId);
        displaySupport.enrichRagHits(rows);
        return rows;
    }

    public Map<String, Object> conversationTraces(long conversationId) {
        Map<String, Object> out = new HashMap<>();
        List<ObsMcpTraceEvent> mcp = mcpTraceEventRepository.listByConversationId(conversationId, 100);
        List<ObsRagHitEvent> rag = ragHitEventRepository.listByConversationId(conversationId, 100);
        displaySupport.enrichMcpTraces(mcp);
        displaySupport.enrichRagHits(rag);
        out.put("mcpTraces", mcp);
        out.put("ragHits", rag);
        out.put("conversationTitle", displaySupport.conversationTitle(conversationId));
        return out;
    }

    private List<Long> resolveConversationIds(Long tid, Long conversationId, String conversationKeyword) {
        if (conversationId != null) {
            return null;
        }
        if (conversationKeyword == null || conversationKeyword.isBlank()) {
            return null;
        }
        return chatConversationRepository.listIdsByTitleLike(tid, conversationKeyword, 200);
    }

    private List<Long> resolveKbIds(Long tid, Long kbId, String kbNameKeyword) {
        if (kbId != null) {
            return null;
        }
        if (kbNameKeyword == null || kbNameKeyword.isBlank()) {
            return null;
        }
        return ragKnowledgeBaseRepository.listIdsByNameLike(tid, kbNameKeyword, 100);
    }

    private static <T> Page<T> emptyPage(long page, long size) {
        Page<T> empty = Page.of(page, size);
        empty.setRecords(List.of());
        empty.setTotal(0);
        return empty;
    }
}
