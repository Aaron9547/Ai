package com.aaron.cloud.common.observability;

import com.aaron.cloud.common.observability.entity.ObsRagHitEvent;
import com.aaron.cloud.common.observability.mapper.ObsRagHitEventMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ObsRagHitEventRepository {

    private final ObsRagHitEventMapper mapper;

    public void insert(ObsRagHitEvent row) {
        mapper.insert(row);
    }

    public void insertBatch(List<ObsRagHitEvent> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (ObsRagHitEvent row : rows) {
            mapper.insert(row);
        }
    }

    public void backfillAssistantMessageId(long conversationId, long userMessageId, long assistantMessageId) {
        mapper.update(
                null,
                Wrappers.<ObsRagHitEvent>lambdaUpdate()
                        .eq(ObsRagHitEvent::getConversationId, conversationId)
                        .eq(ObsRagHitEvent::getUserMessageId, userMessageId)
                        .isNull(ObsRagHitEvent::getAssistantMessageId)
                        .set(ObsRagHitEvent::getAssistantMessageId, assistantMessageId));
    }

    public List<ObsRagHitEvent> listByHitTraceId(String hitTraceId) {
        return mapper.selectList(
                Wrappers.<ObsRagHitEvent>lambdaQuery()
                        .eq(ObsRagHitEvent::getHitTraceId, hitTraceId)
                        .orderByAsc(ObsRagHitEvent::getRankInBatch));
    }

    public Page<ObsRagHitEvent> pageForAdmin(
            Long filterTenantIdOrNull,
            long pageNo,
            long pageSize,
            Long conversationId,
            Collection<Long> conversationIds,
            Long kbId,
            Collection<Long> kbIds,
            Long chunkId,
            String queryKeyword,
            LocalDateTime since) {
        var q = Wrappers.<ObsRagHitEvent>lambdaQuery().orderByDesc(ObsRagHitEvent::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(ObsRagHitEvent::getTenantId, filterTenantIdOrNull);
        }
        if (conversationId != null) {
            q.eq(ObsRagHitEvent::getConversationId, conversationId);
        } else if (conversationIds != null && !conversationIds.isEmpty()) {
            q.in(ObsRagHitEvent::getConversationId, conversationIds);
        }
        if (kbId != null) {
            q.eq(ObsRagHitEvent::getKbId, kbId);
        } else if (kbIds != null && !kbIds.isEmpty()) {
            q.in(ObsRagHitEvent::getKbId, kbIds);
        }
        if (chunkId != null) {
            q.eq(ObsRagHitEvent::getChunkId, chunkId);
        }
        if (queryKeyword != null && !queryKeyword.isBlank()) {
            q.like(ObsRagHitEvent::getQueryText, queryKeyword.trim());
        }
        if (since != null) {
            q.ge(ObsRagHitEvent::getCreatedAt, since);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public List<ObsRagHitEvent> listByConversationId(long conversationId, int limit) {
        return mapper.selectList(
                Wrappers.<ObsRagHitEvent>lambdaQuery()
                        .eq(ObsRagHitEvent::getConversationId, conversationId)
                        .orderByDesc(ObsRagHitEvent::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public int deleteOlderThan(LocalDateTime cutoff, int batchSize) {
        List<Long> ids =
                mapper.selectList(
                                Wrappers.<ObsRagHitEvent>lambdaQuery()
                                        .lt(ObsRagHitEvent::getCreatedAt, cutoff)
                                        .select(ObsRagHitEvent::getId)
                                        .last("LIMIT " + batchSize))
                        .stream()
                        .map(ObsRagHitEvent::getId)
                        .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return mapper.deleteBatchIds(ids);
    }
}
