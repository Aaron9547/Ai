package com.aaron.cloud.rag.runtime;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.common.api.ports.TenantRagRuntimePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalTenantRagRuntimeAdapter implements TenantRagRuntimePort {

    private final TenantRagRuntimeResolver tenantRagRuntimeResolver;

    @Override
    public RagRetrievalMode resolveRetrievalMode(long tenantId) {
        return tenantRagRuntimeResolver.resolveRetrievalMode(tenantId);
    }

    @Override
    public int resolveVectorDimension(long tenantId) {
        return tenantRagRuntimeResolver.resolveVectorDimension(tenantId);
    }

    @Override
    public String resolveRetrievalModeStorage(long tenantId) {
        return tenantRagRuntimeResolver.resolveRetrievalModeStorage(tenantId);
    }

    @Override
    public String storedVectorDimensionRaw(long tenantId) {
        return tenantRagRuntimeResolver.storedVectorDimensionRaw(tenantId);
    }

    @Override
    public String storedRetrievalModeRaw(long tenantId) {
        return tenantRagRuntimeResolver.storedRetrievalModeRaw(tenantId);
    }

    @Override
    public boolean isVectorDimensionLocked(long tenantId) {
        return tenantRagRuntimeResolver.isVectorDimensionLocked(tenantId);
    }

    @Override
    public int processDefaultVectorDimension() {
        return tenantRagRuntimeResolver.processDefaultVectorDimension();
    }

    @Override
    public String normalizeVectorDimensionForPersist(long tenantId, String requestedRaw) {
        return tenantRagRuntimeResolver.normalizeVectorDimensionForPersist(tenantId, requestedRaw);
    }

    @Override
    public String normalizeRetrievalModeForPersist(String requestedRaw) {
        return tenantRagRuntimeResolver.normalizeRetrievalModeForPersist(requestedRaw);
    }
}
