package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.api.enums.rag.RagDocumentDisplayStatus;
import com.aaron.cloud.common.rag.entity.RagDocument;
import com.aaron.cloud.common.rag.mapper.RagDocumentMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    private final RagDocumentMapper mapper;

    public RagDocument findByIdAndTenant(long id, long tenantId) {
        return mapper.selectOne(
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getId, id)
                        .eq(RagDocument::getTenantId, tenantId));
    }

    /** 同知识库下按来源 URL 找最新一篇未删除文档（用于同 URL 重爬覆盖）。 */
    public Optional<RagDocument> findLatestActiveByKbAndSourceUri(long tenantId, long kbId, String sourceUri) {
        if (sourceUri == null || sourceUri.isBlank()) {
            return Optional.empty();
        }
        String uri = sourceUri.trim();
        RagDocument doc =
                mapper.selectOne(
                        Wrappers.<RagDocument>lambdaQuery()
                                .eq(RagDocument::getTenantId, tenantId)
                                .eq(RagDocument::getDeleted, 0)
                                .eq(RagDocument::getSourceUri, uri)
                                .apply(
                                        "id IN (SELECT document_id FROM lnk_rag_kb_document WHERE kb_id = {0})",
                                        kbId)
                                .orderByDesc(RagDocument::getUpdatedAt)
                                .last("LIMIT 1"));
        return Optional.ofNullable(doc);
    }

    public int insert(RagDocument row) {
        return mapper.insert(row);
    }

    public int updateById(RagDocument row) {
        return mapper.updateById(row);
    }

    /** 某知识库下未逻辑删除的文档（经 lnk_rag_kb_document）。 */
    public List<RagDocument> listActiveByKbId(long tenantId, long kbId) {
        return mapper.selectList(
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getTenantId, tenantId)
                        .eq(RagDocument::getDeleted, 0)
                        .apply(
                                "id IN (SELECT document_id FROM lnk_rag_kb_document WHERE kb_id = {0})",
                                kbId)
                        .orderByDesc(RagDocument::getUpdatedAt));
    }

    public long countActiveByKb(long tenantId, long kbId) {
        return mapper.selectCount(
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getTenantId, tenantId)
                        .eq(RagDocument::getDeleted, 0)
                        .apply(
                                "id IN (SELECT document_id FROM lnk_rag_kb_document WHERE kb_id = {0})",
                                kbId));
    }

    /** 某知识库下文档分页；{@code categoryId} 非空时精确匹配分类。 */
    public Page<RagDocument> pageByKb(
            long tenantId,
            long kbId,
            Long categoryId,
            RagDocumentDisplayStatus status,
            String titleKeyword,
            long pageNo,
            long pageSize) {
        var q =
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getTenantId, tenantId)
                        .eq(RagDocument::getDeleted, 0)
                        .apply(
                                "id IN (SELECT document_id FROM lnk_rag_kb_document WHERE kb_id = {0})",
                                kbId);
        if (categoryId != null) {
            q.eq(RagDocument::getCategoryId, categoryId);
        }
        if (status != null) {
            q.eq(RagDocument::getDisplayStatus, status);
        }
        if (titleKeyword != null && !titleKeyword.isBlank()) {
            String kw = titleKeyword.trim();
            q.and(
                    w ->
                            w.like(RagDocument::getTitle, kw)
                                    .or()
                                    .like(RagDocument::getOriginalFilename, kw)
                                    .or()
                                    .like(RagDocument::getSourceUri, kw));
        }
        q.orderByDesc(RagDocument::getUpdatedAt);
        return mapper.selectPage(new Page<>(pageNo, pageSize), q);
    }

    public long countActiveByKbAndCategory(long tenantId, long kbId, long categoryId) {
        return mapper.selectCount(
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getTenantId, tenantId)
                        .eq(RagDocument::getDeleted, 0)
                        .eq(RagDocument::getCategoryId, categoryId)
                        .apply(
                                "id IN (SELECT document_id FROM lnk_rag_kb_document WHERE kb_id = {0})",
                                kbId));
    }

    public List<RagDocument> listByIdsAndTenant(long tenantId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<RagDocument>lambdaQuery()
                        .eq(RagDocument::getTenantId, tenantId)
                        .eq(RagDocument::getDeleted, 0)
                        .in(RagDocument::getId, ids));
    }

    /** 成功召回并落库引用后累加文档维度的命中次数（须带租户条件）。 */
    public int incrementHitCount(long tenantId, long documentId) {
        return mapper.update(
                null,
                Wrappers.<RagDocument>lambdaUpdate()
                        .setSql("hit_count = IFNULL(hit_count, 0) + 1")
                        .eq(RagDocument::getId, documentId)
                        .eq(RagDocument::getTenantId, tenantId)
                        .eq(RagDocument::getDeleted, 0));
    }
}
