package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagVectorRecallHit;
import java.util.List;

/** Milvus 绛夊悜閲忓瓨鍌紱鍙洖杩斿洖鍒嗙墖寮曠敤涓庡垎鏁帮紝姝ｆ枃鐢?{@code RagChunkRepository} 瑙ｆ瀽銆?*/
public interface VectorStorePort {

    void upsertChunks(long tenantId, String collection, List<String> chunkRefs, List<float[]> vectors);

    List<RagVectorRecallHit> searchVectors(long tenantId, String collection, float[] queryVector, int topK);

    default void deleteChunkVectors(long tenantId, String collection, List<String> embeddingRefs) {
        if (embeddingRefs == null || embeddingRefs.isEmpty()) {
            return;
        }
    }
}
