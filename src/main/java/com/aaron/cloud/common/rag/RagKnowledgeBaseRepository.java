package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.rag.mapper.RagKnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagKnowledgeBaseRepository {

    private final RagKnowledgeBaseMapper mapper;

    public List<RagKnowledgeBase> listByTenant(long tenantId) {
        return mapper.selectList(
                Wrappers.<RagKnowledgeBase>lambdaQuery()
                        .eq(RagKnowledgeBase::getTenantId, tenantId)
                        .orderByDesc(RagKnowledgeBase::getUpdatedAt));
    }

    /**
     * 参与对话 RAG 编排的知识库 id 列表（{@link ToggleState#ON} 或列值为 NULL 视为开启，兼容未跑迁移的存量行），按更新时间倒序。
     */
    public List<Long> listIdsWithChatRetrievalEnabled(long tenantId) {
        return mapper.selectList(
                        Wrappers.<RagKnowledgeBase>lambdaQuery()
                                .select(RagKnowledgeBase::getId)
                                .eq(RagKnowledgeBase::getTenantId, tenantId)
                                .and(
                                        w ->
                                                w.eq(RagKnowledgeBase::getChatRetrievalEnabled, ToggleState.ON)
                                                        .or()
                                                        .isNull(RagKnowledgeBase::getChatRetrievalEnabled))
                                .orderByDesc(RagKnowledgeBase::getUpdatedAt))
                .stream()
                .map(RagKnowledgeBase::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    public int insert(RagKnowledgeBase row) {
        return mapper.insert(row);
    }

    public RagKnowledgeBase findByIdAndTenant(long id, long tenantId) {
        return mapper.selectOne(
                Wrappers.<RagKnowledgeBase>lambdaQuery()
                        .eq(RagKnowledgeBase::getId, id)
                        .eq(RagKnowledgeBase::getTenantId, tenantId));
    }

    public int updateById(RagKnowledgeBase row) {
        return mapper.updateById(row);
    }

    public int deleteByIdAndTenant(long id, long tenantId) {
        return mapper.delete(
                Wrappers.<RagKnowledgeBase>lambdaQuery()
                        .eq(RagKnowledgeBase::getId, id)
                        .eq(RagKnowledgeBase::getTenantId, tenantId));
    }
}
