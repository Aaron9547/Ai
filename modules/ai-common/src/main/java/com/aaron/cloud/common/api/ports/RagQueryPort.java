package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import java.util.List;

public interface RagQueryPort {

    List<String> searchSnippets(Long tenantId, Long kbId, String query, int topK);

    /**
     * 结构化引用命中（当前实现为 MySQL 词法路径；与 {@link #searchSnippets} 在词法模式下可并行调用）。
     */
    List<RagCitationHit> searchCitationHits(Long tenantId, Long kbId, String query, int topK);

    /** 在多个知识库内合并检索片段（受 {@code ai.rag.retrieval-mode} 路由）。 */
    List<String> searchSnippetsAcrossKnowledgeBases(Long tenantId, List<Long> kbIds, String query, int topK);

    /** 多库词法引用（供 meta 与命中统计）；与向量模式独立、语义稳定。 */
    List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(Long tenantId, List<Long> kbIds, String query, int topK);
}
