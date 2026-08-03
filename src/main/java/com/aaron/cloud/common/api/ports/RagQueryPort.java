package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile;
import java.util.List;

public interface RagQueryPort {

    default List<String> searchSnippets(Long tenantId, Long kbId, String query, int topK) {
        return searchSnippets(tenantId, kbId, query, topK, null);
    }

    List<String> searchSnippets(
            Long tenantId, Long kbId, String query, int topK, RagRetrievalProfile profile);

    default List<RagCitationHit> searchCitationHits(Long tenantId, Long kbId, String query, int topK) {
        return searchCitationHits(tenantId, kbId, query, topK, null);
    }

    List<RagCitationHit> searchCitationHits(
            Long tenantId, Long kbId, String query, int topK, RagRetrievalProfile profile);

    default List<String> searchSnippetsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK) {
        return searchSnippetsAcrossKnowledgeBases(tenantId, kbIds, query, topK, null);
    }

    List<String> searchSnippetsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK, RagRetrievalProfile profile);

    default List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK) {
        return searchCitationHitsAcrossKnowledgeBases(tenantId, kbIds, query, topK, null);
    }

    List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK, RagRetrievalProfile profile);
}
