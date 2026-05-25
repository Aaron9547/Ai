package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
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
public class LocalRagEmbeddingAdapter implements RagEmbeddingPort {

    private final RagEmbeddingService ragEmbeddingService;

    @Override
    public float[] embed(long tenantId, long kbId, String text) {
        return ragEmbeddingService.embed(tenantId, kbId, text);
    }

    @Override
    public int dimensions(long tenantId) {
        return ragEmbeddingService.dimensions(tenantId);
    }

    @Override
    public float[] embedByVectorModelIdOrHash(long tenantId, Long vectorModelIdOrNull, String text) {
        return ragEmbeddingService.embedByVectorModelIdOrHash(tenantId, vectorModelIdOrNull, text);
    }
}
