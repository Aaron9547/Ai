package com.aaron.cloud.rag.runtime;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.tenant.runtime.TenRuntimeSettingRepository;
import com.aaron.cloud.common.rag.support.RagVectorDimensionSupport;
import com.aaron.cloud.rag.RagQueryEmbeddingHasher;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 租户 RAG 运行时：向量维数（{@link TenantRuntimeSettingKey#RAG_VECTOR_DIMENSION}）与检索模式（
 * {@link TenantRuntimeSettingKey#RAG_RETRIEVAL_MODE}）。维数未配置时回退 {@code ai.providers.milvus.vector-dimension}；
 * 检索模式未配置时回退 {@code ai.rag.retrieval-mode}。维数一旦写入或租户已有分片则不可再改。
 */
@Service
@RequiredArgsConstructor
public class TenantRagRuntimeResolver {

    private final AiProvidersProperties aiProvidersProperties;
    private final AiRagProperties aiRagProperties;
    private final TenRuntimeSettingRepository tenRuntimeSettingRepository;
    private final RagChunkRepository ragChunkRepository;

    /** 进程级 Milvus 向量维数默认值（新租户未显式配置前）。 */
    public int processDefaultVectorDimension() {
        int d = aiProvidersProperties.getMilvus().getVectorDimension();
        return d > 0 ? d : RagQueryEmbeddingHasher.DEFAULT_DIM;
    }

    /** 租户生效维数：已落库的 {@code RAG_VECTOR_DIMENSION} 优先，否则进程默认。 */
    public int resolveVectorDimension(long tenantId) {
        return parseDimensionOrNull(storedVectorDimensionRaw(tenantId)).orElseGet(this::processDefaultVectorDimension);
    }

    /** 库中原始值；无行或空串表示尚未锁定、走 yml 默认。 */
    public String storedVectorDimensionRaw(long tenantId) {
        return tenRuntimeSettingRepository
                .find(tenantId, TenantRuntimeSettingKey.RAG_VECTOR_DIMENSION)
                .map(r -> r.getValueText() == null ? "" : r.getValueText().trim())
                .orElse("");
    }

    public boolean isVectorDimensionLocked(long tenantId) {
        if (!storedVectorDimensionRaw(tenantId).isBlank()) {
            return true;
        }
        return ragChunkRepository.tenantHasAnyChunks(tenantId);
    }

    /**
     * 管理端保存前校验。{@code requestedRaw} 为空表示不写入键、继续用进程默认（仅当未锁定时允许）。
     */
    public void validateVectorDimensionSave(long tenantId, String requestedRaw) {
        String stored = storedVectorDimensionRaw(tenantId);
        Integer requested =
                parseDimensionOrNull(requestedRaw == null ? "" : requestedRaw.trim()).orElse(null);
        Integer storedDim = parseDimensionOrNull(stored).orElse(null);
        if (isVectorDimensionLocked(tenantId)) {
            if (storedDim != null && requested != null && !requested.equals(storedDim)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "向量维数已锁定为 "
                                + storedDim
                                + "，不可修改。更换维数须新建租户并全库重建索引（Milvus collection 维数不兼容）。");
            }
            if (storedDim != null && requested == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "向量维数已锁定，不可清空；当前为 " + storedDim);
            }
            return;
        }
        if (requested == null) {
            return;
        }
        if (ragChunkRepository.tenantHasAnyChunks(tenantId)) {
            int effective = processDefaultVectorDimension();
            if (!requested.equals(effective)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "租户已有知识库分片，首次设定向量维数须与当前生效值 "
                                + effective
                                + " 一致（与进程 ai.providers.milvus.vector-dimension 一致）");
            }
        }
    }

    /** 规范化后写入库的十进制字符串；{@code null} 表示不 upsert 该键。 */
    public String normalizeVectorDimensionForPersist(long tenantId, String requestedRaw) {
        validateVectorDimensionSave(tenantId, requestedRaw);
        if (isVectorDimensionLocked(tenantId)) {
            String stored = storedVectorDimensionRaw(tenantId);
            if (!stored.isBlank()) {
                return stored;
            }
        }
        Integer requested =
                parseDimensionOrNull(requestedRaw == null ? "" : requestedRaw.trim()).orElse(null);
        if (requested == null) {
            return null;
        }
        return String.valueOf(requested);
    }

    public RagRetrievalMode resolveRetrievalMode(long tenantId) {
        String raw = storedRetrievalModeRaw(tenantId);
        if (raw.isBlank()) {
            return aiRagProperties.resolvedRetrievalMode();
        }
        return RagRetrievalMode.fromYaml(raw);
    }

    public String resolveRetrievalModeStorage(long tenantId) {
        return resolveRetrievalMode(tenantId).getStorageValue();
    }

    public String storedRetrievalModeRaw(long tenantId) {
        return tenRuntimeSettingRepository
                .find(tenantId, TenantRuntimeSettingKey.RAG_RETRIEVAL_MODE)
                .map(r -> r.getValueText() == null ? "" : r.getValueText().trim())
                .orElse("");
    }

    public String normalizeRetrievalModeForPersist(String requestedRaw) {
        if (requestedRaw == null || requestedRaw.isBlank()) {
            return "";
        }
        String t = requestedRaw.trim().toLowerCase(Locale.ROOT);
        for (RagRetrievalMode m : RagRetrievalMode.values()) {
            if (m.getStorageValue().equals(t) || m.name().equalsIgnoreCase(t)) {
                return m.getStorageValue();
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "RAG_RETRIEVAL_MODE 须为 milvus 或 milvus_es_hybrid，或留空使用进程默认");
    }

    public static Optional<Integer> parseDimensionOrNull(String raw) {
        return RagVectorDimensionSupport.parseDimensionOrNull(raw);
    }
}
