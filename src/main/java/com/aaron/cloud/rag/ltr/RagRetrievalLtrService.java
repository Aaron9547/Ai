package com.aaron.cloud.rag.ltr;

import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningEffectiveService;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.aaron.cloud.rag.RagRetrievalScoredHit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 租户 LTR 开关 + 权重加载 + 候选重排。 */
@Service
@RequiredArgsConstructor
public class RagRetrievalLtrService {

    private final RagRetrievalTuningEffectiveService ragRetrievalTuningEffectiveService;
    private final RagLtrModelStore ragLtrModelStore;

    public List<RagRetrievalScoredHit> rerankIfEnabled(
            long tenantId, String query, List<RagRetrievalScoredHit> candidates) {
        RagRetrievalTuningRuntime tuning = ragRetrievalTuningEffectiveService.effective(tenantId);
        if (!tuning.hybridLtrEnabled() || candidates == null || candidates.size() <= 1) {
            return candidates;
        }
        double[] weights = ragLtrModelStore.resolveWeights(tenantId, tuning);
        return RagLtrFeatureExtractor.rank(query, candidates, weights);
    }

    public int candidatePoolSize(long tenantId, int topK) {
        RagRetrievalTuningRuntime tuning = ragRetrievalTuningEffectiveService.effective(tenantId);
        return Math.max(topK, topK * tuning.resolvedLtrCandidateMultiplier());
    }
}
