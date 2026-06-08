package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.api.enums.eval.EvalRunStatus;
import com.aaron.cloud.common.api.enums.rag.RagQualityAssessmentScope;
import com.aaron.cloud.common.rag.entity.RagQualityAssessment;
import com.aaron.cloud.common.rag.mapper.RagQualityAssessmentMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagQualityAssessmentRepository {

    private final RagQualityAssessmentMapper mapper;

    public void insert(RagQualityAssessment row) {
        mapper.insert(row);
    }

    public void updateById(RagQualityAssessment row) {
        mapper.updateById(row);
    }

    public RagQualityAssessment findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        return mapper.selectOne(
                Wrappers.<RagQualityAssessment>lambdaQuery().eq(RagQualityAssessment::getRunId, runId.trim()));
    }

    public Page<RagQualityAssessment> pageForAdmin(
            Long filterTenantIdOrNull,
            long pageNo,
            long pageSize,
            RagQualityAssessmentScope scope,
            Long assistantMessageId,
            Long chunkId,
            Collection<Long> conversationIds,
            String queryKeyword,
            LocalDateTime since) {
        var q = Wrappers.<RagQualityAssessment>lambdaQuery().orderByDesc(RagQualityAssessment::getCreatedAt);
        if (filterTenantIdOrNull != null) {
            q.eq(RagQualityAssessment::getTenantId, filterTenantIdOrNull);
        }
        if (scope != null) {
            q.eq(RagQualityAssessment::getScope, scope);
        }
        if (assistantMessageId != null) {
            q.eq(RagQualityAssessment::getAssistantMessageId, assistantMessageId);
        }
        if (chunkId != null) {
            q.eq(RagQualityAssessment::getChunkId, chunkId);
        }
        if (conversationIds != null && !conversationIds.isEmpty()) {
            q.in(RagQualityAssessment::getConversationId, conversationIds);
        }
        if (queryKeyword != null && !queryKeyword.isBlank()) {
            q.like(RagQualityAssessment::getQueryText, queryKeyword.trim());
        }
        if (since != null) {
            q.ge(RagQualityAssessment::getCreatedAt, since);
        }
        return mapper.selectPage(Page.of(pageNo, pageSize), q);
    }

    public List<RagQualityAssessment> listByAssistantMessageId(long assistantMessageId, int limit) {
        return mapper.selectList(
                Wrappers.<RagQualityAssessment>lambdaQuery()
                        .eq(RagQualityAssessment::getAssistantMessageId, assistantMessageId)
                        .orderByDesc(RagQualityAssessment::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
    }

    public int deleteOlderThan(LocalDateTime cutoff, int batchSize) {
        List<Long> ids =
                mapper.selectList(
                                Wrappers.<RagQualityAssessment>lambdaQuery()
                                        .lt(RagQualityAssessment::getCreatedAt, cutoff)
                                        .select(RagQualityAssessment::getId)
                                        .last("LIMIT " + batchSize))
                        .stream()
                        .map(RagQualityAssessment::getId)
                        .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return mapper.deleteBatchIds(ids);
    }
}
