package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.rag.entity.LnkRagDocumentChunk;
import com.aaron.cloud.common.rag.mapper.LnkRagDocumentChunkMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LnkRagDocumentChunkRepository {

    private final LnkRagDocumentChunkMapper mapper;

    public int insert(LnkRagDocumentChunk row) {
        return mapper.insert(row);
    }

    public List<LnkRagDocumentChunk> listByDocumentId(long documentId) {
        return mapper.selectList(
                Wrappers.<LnkRagDocumentChunk>lambdaQuery()
                        .eq(LnkRagDocumentChunk::getDocumentId, documentId)
                        .orderByAsc(LnkRagDocumentChunk::getSeq));
    }

    /** 按分片 id 批量解析所属文档与顺序（用于 RAG 引用组装）。 */
    public List<LnkRagDocumentChunk> listByChunkIds(Collection<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<LnkRagDocumentChunk>lambdaQuery().in(LnkRagDocumentChunk::getChunkId, chunkIds));
    }

    public int deleteByDocumentId(long documentId) {
        return mapper.delete(
                Wrappers.<LnkRagDocumentChunk>lambdaQuery().eq(LnkRagDocumentChunk::getDocumentId, documentId));
    }

    public int deleteByDocumentIdAndChunkId(long documentId, long chunkId) {
        return mapper.delete(
                Wrappers.<LnkRagDocumentChunk>lambdaQuery()
                        .eq(LnkRagDocumentChunk::getDocumentId, documentId)
                        .eq(LnkRagDocumentChunk::getChunkId, chunkId));
    }

    public int updateById(LnkRagDocumentChunk row) {
        return mapper.updateById(row);
    }
}
