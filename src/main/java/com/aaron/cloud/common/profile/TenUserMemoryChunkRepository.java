package com.aaron.cloud.common.profile;

import com.aaron.cloud.common.profile.entity.TenUserMemoryChunk;
import com.aaron.cloud.common.profile.mapper.TenUserMemoryChunkMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TenUserMemoryChunkRepository {

    private final TenUserMemoryChunkMapper mapper;

    public int insert(TenUserMemoryChunk row) {
        return mapper.insert(row);
    }

    public List<TenUserMemoryChunk> listRecent(long tenantId, String subjectKey, int limit) {
        return mapper.selectList(
                Wrappers.<TenUserMemoryChunk>lambdaQuery()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, subjectKey)
                        .orderByDesc(TenUserMemoryChunk::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
    }

    /** 关键词粗召回：与当前用户输入子串匹配的记忆片段（具体层 RAG 占位实现）。 */
    public List<TenUserMemoryChunk> searchLexical(long tenantId, String subjectKey, String query, int limit) {
        if (query == null || query.isBlank()) {
            return listRecent(tenantId, subjectKey, limit);
        }
        String q = query.trim();
        if (q.length() > 128) {
            q = q.substring(0, 128);
        }
        q = q.replace('%', ' ').replace('_', ' ');
        return mapper.selectList(
                Wrappers.<TenUserMemoryChunk>lambdaQuery()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, subjectKey)
                        .like(TenUserMemoryChunk::getContentSnippet, q)
                        .orderByDesc(TenUserMemoryChunk::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }

    public int reassignSubject(long tenantId, String fromSubjectKey, String toSubjectKey) {
        return mapper.update(
                null,
                new LambdaUpdateWrapper<TenUserMemoryChunk>()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, fromSubjectKey)
                        .set(TenUserMemoryChunk::getSubjectKey, toSubjectKey));
    }

    public int deleteByTenantAndSubject(long tenantId, String subjectKey) {
        return mapper.delete(
                Wrappers.<TenUserMemoryChunk>lambdaQuery()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, subjectKey));
    }

    public long countByTenantAndSubject(long tenantId, String subjectKey) {
        return mapper.selectCount(
                Wrappers.<TenUserMemoryChunk>lambdaQuery()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, subjectKey));
    }

    public Page<TenUserMemoryChunk> pageByTenantAndSubject(
            long tenantId, String subjectKey, long pageNo, long pageSize) {
        return mapper.selectPage(
                Page.of(pageNo, pageSize),
                Wrappers.<TenUserMemoryChunk>lambdaQuery()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, subjectKey)
                        .orderByDesc(TenUserMemoryChunk::getCreatedAt));
    }

    public List<TenUserMemoryChunk> listByIds(long tenantId, String subjectKey, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<TenUserMemoryChunk>lambdaQuery()
                        .eq(TenUserMemoryChunk::getTenantId, tenantId)
                        .eq(TenUserMemoryChunk::getSubjectKey, subjectKey)
                        .in(TenUserMemoryChunk::getId, ids));
    }

    public int updateVectorRef(long id, String vectorRef) {
        return mapper.update(
                null,
                new LambdaUpdateWrapper<TenUserMemoryChunk>()
                        .eq(TenUserMemoryChunk::getId, id)
                        .set(TenUserMemoryChunk::getVectorRef, vectorRef));
    }
}
