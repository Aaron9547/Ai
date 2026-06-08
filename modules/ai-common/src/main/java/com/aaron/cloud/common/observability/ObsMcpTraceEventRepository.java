package com.aaron.cloud.common.observability;

import com.aaron.cloud.common.observability.entity.ObsMcpTraceEvent;
import com.aaron.cloud.common.observability.mapper.ObsMcpTraceEventMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ObsMcpTraceEventRepository {

    private final ObsMcpTraceEventMapper mapper;

    public void insert(ObsMcpTraceEvent row) {
        mapper.insert(row);
    }

    public ObsMcpTraceEvent findByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<ObsMcpTraceEvent>lambdaQuery().eq(ObsMcpTraceEvent::getTraceId, traceId.trim()));
    }

    public Page<ObsMcpTraceEvent> pageForAdmin(
            Long filterTenantIdOrNull,
            long pageNo,
            long pageSize,
            Long conversationId,
            Collection<Long> conversationIds,
            String qualifiedToolName,
            Boolean success,
            LocalDateTime since) {
        var q = Wrappers.<ObsMcpTraceEvent>lambdaQuery().orderByDesc(ObsMcpTraceEvent::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(ObsMcpTraceEvent::getTenantId, filterTenantIdOrNull);
        }
        if (conversationId != null) {
            q.eq(ObsMcpTraceEvent::getConversationId, conversationId);
        } else if (conversationIds != null && !conversationIds.isEmpty()) {
            q.in(ObsMcpTraceEvent::getConversationId, conversationIds);
        }
        if (qualifiedToolName != null && !qualifiedToolName.isBlank()) {
            q.like(ObsMcpTraceEvent::getQualifiedToolName, qualifiedToolName.trim());
        }
        if (success != null) {
            q.eq(ObsMcpTraceEvent::getSuccess, success);
        }
        if (since != null) {
            q.ge(ObsMcpTraceEvent::getCreatedAt, since);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public List<ObsMcpTraceEvent> listByConversationId(long conversationId, int limit) {
        return mapper.selectList(
                Wrappers.<ObsMcpTraceEvent>lambdaQuery()
                        .eq(ObsMcpTraceEvent::getConversationId, conversationId)
                        .orderByDesc(ObsMcpTraceEvent::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public int deleteOlderThan(LocalDateTime cutoff, int batchSize) {
        List<Long> ids =
                mapper.selectList(
                                Wrappers.<ObsMcpTraceEvent>lambdaQuery()
                                        .lt(ObsMcpTraceEvent::getCreatedAt, cutoff)
                                        .select(ObsMcpTraceEvent::getId)
                                        .last("LIMIT " + batchSize))
                        .stream()
                        .map(ObsMcpTraceEvent::getId)
                        .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return mapper.deleteBatchIds(ids);
    }
}
