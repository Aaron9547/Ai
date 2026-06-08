package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile;
import com.aaron.cloud.common.api.ports.RagQueryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ai.remoting",
        name = "mode",
        havingValue = "local",
        matchIfMissing = true)
public class LocalRagQueryAdapter implements RagQueryPort {

    private final RagQueryBridgeService ragQueryBridgeService;

    @Override
    public List<String> searchSnippets(
            Long tenantId, Long kbId, String query, int topK, RagRetrievalProfile profile) {
        return ragQueryBridgeService.searchSnippets(tenantId, kbId, query, topK, profile);
    }

    @Override
    public List<RagCitationHit> searchCitationHits(
            Long tenantId, Long kbId, String query, int topK, RagRetrievalProfile profile) {
        return ragQueryBridgeService.searchCitationHits(tenantId, kbId, query, topK, profile);
    }

    @Override
    public List<String> searchSnippetsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK, RagRetrievalProfile profile) {
        return ragQueryBridgeService.searchSnippetsAcrossKnowledgeBases(
                tenantId, kbIds, query, topK, profile);
    }

    @Override
    public List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK, RagRetrievalProfile profile) {
        return ragQueryBridgeService.searchCitationHitsAcrossKnowledgeBases(
                tenantId, kbIds, query, topK, profile);
    }
}
