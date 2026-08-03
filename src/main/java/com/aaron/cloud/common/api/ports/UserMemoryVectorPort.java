package com.aaron.cloud.common.api.ports;

import java.util.List;

/** 用户记忆 Milvus 向量索引；实现位于 {@code rag} 域（{@code ai.memory.vector-enabled=true} 时装配）。 */
public interface UserMemoryVectorPort {

    void upsertVector(long tenantId, String subjectKey, long chunkId, float[] vector);

    void deleteByTenantAndSubject(long tenantId, String subjectKey);

    List<Long> searchChunkIds(long tenantId, String subjectKey, float[] queryVector, int topK);
}
