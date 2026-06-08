package com.aaron.cloud.rag.rest.internal;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.dto.rag.RagEmbedRequest;
import com.aaron.cloud.common.api.dto.rag.RagQueryCitationHitsRequest;
import com.aaron.cloud.common.api.dto.rag.RagQuerySnippetsRequest;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.rag.RagEmbeddingService;
import com.aaron.cloud.rag.RagQueryBridgeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/rag")
@RequiredArgsConstructor
public class RagInternalRestController {

    private final RagQueryBridgeService ragQueryBridgeService;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final RagEmbeddingService ragEmbeddingService;

    @PostMapping("/query/snippets")
    List<String> searchSnippets(@RequestBody RagQuerySnippetsRequest request) {
        if (request.isAcrossKnowledgeBases()) {
            return ragQueryBridgeService.searchSnippetsAcrossKnowledgeBases(
                    request.getTenantId(),
                    request.getKbIds(),
                    request.getQuery(),
                    request.getTopK(),
                    request.getProfile());
        }
        return ragQueryBridgeService.searchSnippets(
                request.getTenantId(),
                request.getKbId(),
                request.getQuery(),
                request.getTopK(),
                request.getProfile());
    }

    @PostMapping("/query/citation-hits")
    List<RagCitationHit> searchCitationHits(@RequestBody RagQueryCitationHitsRequest request) {
        if (request.isAcrossKnowledgeBases()) {
            return ragQueryBridgeService.searchCitationHitsAcrossKnowledgeBases(
                    request.getTenantId(),
                    request.getKbIds(),
                    request.getQuery(),
                    request.getTopK(),
                    request.getProfile());
        }
        return ragQueryBridgeService.searchCitationHits(
                request.getTenantId(),
                request.getKbId(),
                request.getQuery(),
                request.getTopK(),
                request.getProfile());
    }

    @GetMapping("/embed/dimensions")
    int dimensions(@RequestParam("tenantId") long tenantId) {
        return ragEmbeddingPort.dimensions(tenantId);
    }

    @PostMapping("/embed")
    float[] embed(@RequestBody RagEmbedRequest request) {
        if (request.isByVectorModelIdOrHash()) {
            return ragEmbeddingService.embedByVectorModelIdOrHash(
                    request.getTenantId(), request.getVectorModelIdOrNull(), request.getText());
        }
        if (request.getKbId() == null) {
            return ragEmbeddingPort.embed(request.getTenantId(), 0L, request.getText());
        }
        return ragEmbeddingPort.embed(request.getTenantId(), request.getKbId(), request.getText());
    }
}
