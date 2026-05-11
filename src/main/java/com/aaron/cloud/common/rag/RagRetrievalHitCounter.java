package com.aaron.cloud.common.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话 RAG 引用落库后回写 {@code rag_chunk}/{@code rag_document} 命中计数；失败仅打日志，不回滚会话消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalHitCounter {

    private final RagChunkRepository ragChunkRepository;
    private final RagDocumentRepository ragDocumentRepository;

    public void recordHits(long tenantId, List<RagCitationHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (RagCitationHit h : hits) {
            try {
                int c = ragChunkRepository.incrementHitCount(tenantId, h.chunkId());
                if (c == 0) {
                    log.warn(
                            "rag chunk hit_count skip (no row) tenantId={} chunkId={} documentId={}",
                            tenantId,
                            h.chunkId(),
                            h.documentId());
                }
            } catch (Exception e) {
                log.warn(
                        "rag chunk hit_count update failed tenantId={} chunkId={}",
                        tenantId,
                        h.chunkId(),
                        e);
            }
            try {
                int d = ragDocumentRepository.incrementHitCount(tenantId, h.documentId());
                if (d == 0) {
                    log.warn(
                            "rag document hit_count skip (no row) tenantId={} documentId={}",
                            tenantId,
                            h.documentId());
                }
            } catch (Exception e) {
                log.warn(
                        "rag document hit_count update failed tenantId={} documentId={}",
                        tenantId,
                        h.documentId(),
                        e);
            }
        }
    }
}
