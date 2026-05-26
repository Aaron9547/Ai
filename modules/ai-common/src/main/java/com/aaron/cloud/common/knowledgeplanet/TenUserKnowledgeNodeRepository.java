package com.aaron.cloud.common.knowledgeplanet;

import com.aaron.cloud.common.knowledgeplanet.entity.TenUserKnowledgeNode;
import com.aaron.cloud.common.knowledgeplanet.mapper.TenUserKnowledgeNodeMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenUserKnowledgeNodeRepository {

    private final TenUserKnowledgeNodeMapper mapper;

    public int insert(TenUserKnowledgeNode row) {
        return mapper.insert(row);
    }

    public List<TenUserKnowledgeNode> listRecent(long tenantId, String subjectKey, int limit) {
        return mapper.selectList(
                Wrappers.<TenUserKnowledgeNode>lambdaQuery()
                        .eq(TenUserKnowledgeNode::getTenantId, tenantId)
                        .eq(TenUserKnowledgeNode::getSubjectKey, subjectKey)
                        .orderByDesc(TenUserKnowledgeNode::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    public List<TenUserKnowledgeNode> listByConversation(
            long tenantId, String subjectKey, long conversationId, int limit) {
        return mapper.selectList(
                Wrappers.<TenUserKnowledgeNode>lambdaQuery()
                        .eq(TenUserKnowledgeNode::getTenantId, tenantId)
                        .eq(TenUserKnowledgeNode::getSubjectKey, subjectKey)
                        .eq(TenUserKnowledgeNode::getConversationId, conversationId)
                        .orderByDesc(TenUserKnowledgeNode::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }

    public long countBySubject(long tenantId, String subjectKey) {
        return mapper.selectCount(
                Wrappers.<TenUserKnowledgeNode>lambdaQuery()
                        .eq(TenUserKnowledgeNode::getTenantId, tenantId)
                        .eq(TenUserKnowledgeNode::getSubjectKey, subjectKey));
    }

    /** 过去一段时间内有节点的登录用户 subject_key（{@code u:*}）去重。 */
    public List<String> listDistinctUserSubjectKeysSince(long tenantId, LocalDateTime since) {
        return mapper.selectList(
                        Wrappers.<TenUserKnowledgeNode>lambdaQuery()
                                .select(TenUserKnowledgeNode::getSubjectKey)
                                .eq(TenUserKnowledgeNode::getTenantId, tenantId)
                                .likeRight(TenUserKnowledgeNode::getSubjectKey, "u:")
                                .ge(TenUserKnowledgeNode::getCreatedAt, since))
                .stream()
                .map(TenUserKnowledgeNode::getSubjectKey)
                .distinct()
                .toList();
    }
}
