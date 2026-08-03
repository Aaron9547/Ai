package com.aaron.cloud.rag;

import com.aaron.cloud.common.rag.LnkRagDocumentChunkRepository;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.entity.LnkRagDocumentChunk;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.api.enums.rag.RagChunkRetrievalEnabled;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 文档下全部分片与向量清理（重爬覆盖、删除文档时复用）。 */
@Service
@RequiredArgsConstructor
public class RagDocumentChunkPurgeService {

    private final RagChunkRepository ragChunkRepository;
    private final LnkRagDocumentChunkRepository lnkRagDocumentChunkRepository;
    private final VectorStorePort vectorStorePort;

    public void purgeAllChunksForDocument(long tenantId, long kbId, long documentId) {
        List<LnkRagDocumentChunk> links = lnkRagDocumentChunkRepository.listByDocumentId(documentId);
        if (links.isEmpty()) {
            return;
        }
        List<Long> chunkIds = links.stream().map(LnkRagDocumentChunk::getChunkId).distinct().toList();
        List<String> refs = new ArrayList<>();
        for (Long cid : chunkIds) {
            if (cid == null) {
                continue;
            }
            RagChunk ch = ragChunkRepository.findByIdAndTenant(cid, tenantId);
            if (ch == null) {
                continue;
            }
            if (ch.getRetrievalEnabled() == RagChunkRetrievalEnabled.ENABLED) {
                String ref =
                        ch.getEmbeddingRef() != null && !ch.getEmbeddingRef().isBlank()
                                ? ch.getEmbeddingRef()
                                : String.valueOf(ch.getId());
                refs.add(ref);
            }
        }
        if (!refs.isEmpty()) {
            vectorStorePort.deleteChunkVectors(tenantId, "kb_" + kbId, refs);
        }
        if (!chunkIds.isEmpty()) {
            ragChunkRepository.markDeleted(tenantId, chunkIds);
        }
        lnkRagDocumentChunkRepository.deleteByDocumentId(documentId);
    }
}
