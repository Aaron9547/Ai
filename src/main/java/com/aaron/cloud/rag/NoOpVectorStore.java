package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagVectorRecallHit;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.providers.vector-store", havingValue = "local", matchIfMissing = true)
public class NoOpVectorStore implements VectorStorePort {

    @Override
    public void upsertChunks(long tenantId, String collection, List<String> chunkRefs, List<float[]> vectors) {
        // no-op
    }

    @Override
    public List<RagVectorRecallHit> searchVectors(long tenantId, String collection, float[] queryVector, int topK) {
        return Collections.emptyList();
    }

    @Override
    public void deleteChunkVectors(long tenantId, String collection, List<String> embeddingRefs) {
        VectorStorePort.super.deleteChunkVectors(tenantId, collection, embeddingRefs);
    }
}
