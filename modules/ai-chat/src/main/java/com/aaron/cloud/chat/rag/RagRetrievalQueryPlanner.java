package com.aaron.cloud.chat.rag;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningEffectiveService;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 编排 RAG 问句改写、语义门控与简单/复杂分流。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalQueryPlanner {

    private final RagRetrievalTuningEffectiveService ragRetrievalTuningEffectiveService;
    private final RagQueryRewriteService ragQueryRewriteService;
    private final RagRewriteSemanticGate ragRewriteSemanticGate;
    private final RagQueryComplexityClassifier ragQueryComplexityClassifier;

    public RagRetrievalQueryPlan plan(
            TenantSnapshot snap,
            List<ModelChatRequest.MessageTurn> history,
            String originalQuery,
            long conversationId,
            Long embeddingKbId) {
        return plan(snap, history, originalQuery, conversationId, embeddingKbId, false);
    }

    public RagRetrievalQueryPlan plan(
            TenantSnapshot snap,
            List<ModelChatRequest.MessageTurn> history,
            String originalQuery,
            long conversationId,
            Long embeddingKbId,
            boolean skipRewrite) {
        String original = originalQuery == null ? "" : originalQuery.trim();
        if (original.isEmpty()) {
            return new RagRetrievalQueryPlan(
                    "", "", false, false, null, RagRetrievalProfile.COMPLEX_HYBRID);
        }
        RagRetrievalTuningRuntime tuning = ragRetrievalTuningEffectiveService.effective(snap.getTenantId());
        String candidate = original;
        boolean rewriteApplied = false;
        boolean rewriteRejected = false;
        Double rewriteSimilarity = null;

        if (!skipRewrite && tuning.rewriteEnabled()) {
            String rewritten =
                    ragQueryRewriteService.rewriteForRetrieval(
                            snap, original, history, conversationId, tuning);
            if (!rewritten.isBlank() && !rewritten.equals(original)) {
                RagRewriteSemanticGate.GateOutcome gate =
                        ragRewriteSemanticGate.evaluate(
                                snap.getTenantId(),
                                embeddingKbId,
                                history,
                                rewritten,
                                tuning);
                rewriteSimilarity = gate.similarity();
                if (gate.accepted()) {
                    candidate = rewritten;
                    rewriteApplied = true;
                    log.info(
                            "[知识库检索] 问句改写采纳：租户 {}，相似度 {}，原文 [{}]，检索词 [{}]",
                            snap.getTenantId(),
                            rewriteSimilarity,
                            RagQueryRewriteService.clipForLog(original),
                            RagQueryRewriteService.clipForLog(candidate));
                } else {
                    rewriteRejected = true;
                    log.info(
                            "[知识库检索] 问句改写丢弃（语义低于 {}）：租户 {}，相似度 {}，沿用原文 [{}]",
                            tuning.resolvedRewriteSemanticMinSimilarity(),
                            snap.getTenantId(),
                            rewriteSimilarity,
                            RagQueryRewriteService.clipForLog(original));
                }
            }
        }

        RagRetrievalProfile profile =
                ragQueryComplexityClassifier.classify(candidate, tuning);
        return new RagRetrievalQueryPlan(
                original, candidate, rewriteApplied, rewriteRejected, rewriteSimilarity, profile);
    }
}
