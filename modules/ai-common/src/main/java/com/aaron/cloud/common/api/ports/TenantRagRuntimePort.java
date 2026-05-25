package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;

/** 租户 RAG 运行时解析（向量维数、检索模式）；实现位于 {@code rag} 域。 */
public interface TenantRagRuntimePort {

    RagRetrievalMode resolveRetrievalMode(long tenantId);

    int resolveVectorDimension(long tenantId);

    String resolveRetrievalModeStorage(long tenantId);

    String storedVectorDimensionRaw(long tenantId);

    String storedRetrievalModeRaw(long tenantId);

    boolean isVectorDimensionLocked(long tenantId);

    int processDefaultVectorDimension();

    /** 管理端保存前校验并规范化；{@code null} 表示不 upsert 该键。 */
    String normalizeVectorDimensionForPersist(long tenantId, String requestedRaw);

    String normalizeRetrievalModeForPersist(String requestedRaw);
}
