package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 合并进程 yml {@code ai.rag.retrieval-tuning} 与租户 {@code RAG_RETRIEVAL_TUNING_JSON}。 */
@Service
@RequiredArgsConstructor
public class RagRetrievalTuningEffectiveService {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final AiRagProperties aiRagProperties;
    private final ObjectMapper objectMapper;

    public RagRetrievalTuningRuntime effective(long tenantId) {
        String raw =
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.RAG_RETRIEVAL_TUNING_JSON);
        RagRetrievalTuningRuntime tenant = RagRetrievalTuningRuntime.parse(raw, objectMapper);
        return RagRetrievalTuningRuntime.merge(fromYml(aiRagProperties.getRetrievalTuning()), tenant);
    }

    private static RagRetrievalTuningRuntime fromYml(AiRagProperties.RetrievalTuning yml) {
        if (yml == null) {
            return RagRetrievalTuningRuntime.defaults();
        }
        String rewriteModelId =
                yml.getRewriteModelId() == null || yml.getRewriteModelId().isBlank()
                        ? null
                        : yml.getRewriteModelId().trim();
        return new RagRetrievalTuningRuntime(
                yml.isRewriteEnabled(),
                yml.getRewriteSemanticMinSimilarity(),
                rewriteModelId,
                yml.getRewriteContextMaxChars(),
                yml.isSimpleQueryFastPathEnabled(),
                yml.getSimpleQueryMaxChars(),
                yml.isHybridLtrEnabled(),
                yml.getLtrCandidateMultiplier(),
                null,
                null,
                yml.getLtrModelVersion());
    }
}
