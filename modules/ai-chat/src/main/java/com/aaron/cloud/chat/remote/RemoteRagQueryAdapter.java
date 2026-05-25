package com.aaron.cloud.chat.remote;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.dto.rag.RagQueryCitationHitsRequest;
import com.aaron.cloud.common.api.dto.rag.RagQuerySnippetsRequest;
import com.aaron.cloud.common.api.ports.RagQueryPort;
import com.aaron.cloud.chat.remote.RemoteRagQueryClient;
import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
public class RemoteRagQueryAdapter implements RagQueryPort {

    private final RemoteRagQueryClient remoteRagQueryClient;

    @Override
    public List<String> searchSnippets(Long tenantId, Long kbId, String query, int topK) {
        return invokeSnippets(
                RagQuerySnippetsRequest.builder()
                        .tenantId(tenantId)
                        .kbId(kbId)
                        .query(query)
                        .topK(topK)
                        .acrossKnowledgeBases(false)
                        .build());
    }

    @Override
    public List<RagCitationHit> searchCitationHits(Long tenantId, Long kbId, String query, int topK) {
        return invokeCitationHits(
                RagQueryCitationHitsRequest.builder()
                        .tenantId(tenantId)
                        .kbId(kbId)
                        .query(query)
                        .topK(topK)
                        .acrossKnowledgeBases(false)
                        .build());
    }

    @Override
    public List<String> searchSnippetsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK) {
        return invokeSnippets(
                RagQuerySnippetsRequest.builder()
                        .tenantId(tenantId)
                        .kbIds(kbIds)
                        .query(query)
                        .topK(topK)
                        .acrossKnowledgeBases(true)
                        .build());
    }

    @Override
    public List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK) {
        return invokeCitationHits(
                RagQueryCitationHitsRequest.builder()
                        .tenantId(tenantId)
                        .kbIds(kbIds)
                        .query(query)
                        .topK(topK)
                        .acrossKnowledgeBases(true)
                        .build());
    }

    private List<String> invokeSnippets(RagQuerySnippetsRequest request) {
        try {
            return remoteRagQueryClient.searchSnippets(request);
        } catch (FeignException e) {
            throw ragUnavailable(e);
        }
    }

    private List<RagCitationHit> invokeCitationHits(RagQueryCitationHitsRequest request) {
        try {
            return remoteRagQueryClient.searchCitationHits(request);
        } catch (FeignException e) {
            throw ragUnavailable(e);
        }
    }

    private static ResponseStatusException ragUnavailable(FeignException e) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "RAG 模块不可用，无法执行检索", e);
    }
}
