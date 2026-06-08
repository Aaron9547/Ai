package com.aaron.cloud.chat.rag;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.aaron.cloud.common.util.TextClamp;
import com.aaron.cloud.chat.websearch.cache.WebSearchVectorSimilarity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 改写问句与对话上下文的 embedding 余弦校验（独立于 KB 召回阈值）。 */
@Service
@RequiredArgsConstructor
public class RagRewriteSemanticGate {

    private final RagEmbeddingPort ragEmbeddingPort;

    public record GateOutcome(boolean accepted, double similarity) {}

    public GateOutcome evaluate(
            long tenantId,
            List<ModelChatRequest.MessageTurn> history,
            String rewrittenQuery,
            RagRetrievalTuningRuntime tuning,
            Long conversationId) {
        String context = buildContextText(history, tuning.resolvedRewriteContextMaxChars());
        String rewritten = rewrittenQuery == null ? "" : rewrittenQuery.trim();
        if (context.isBlank() || rewritten.isBlank()) {
            return new GateOutcome(true, 1.0d);
        }
        float[] ctxVec = ragEmbeddingPort.embed(tenantId, 0L, context);
        float[] qVec = ragEmbeddingPort.embed(tenantId, 0L, rewritten);
        double sim = WebSearchVectorSimilarity.cosine(ctxVec, qVec);
        double min = tuning.resolvedRewriteSemanticMinSimilarity();
        return new GateOutcome(sim >= min, sim);
    }

    static String buildContextText(List<ModelChatRequest.MessageTurn> history, int maxChars) {
        if (history == null || history.isEmpty() || maxChars <= 0) {
            return "";
        }
        var sb = new StringBuilder();
        for (ModelChatRequest.MessageTurn t : history) {
            if (t == null || t.getRole() == null) {
                continue;
            }
            String role = t.getRole().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String body = t.getContent() == null ? "" : t.getContent().trim();
            if (body.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(role).append(": ").append(body);
        }
        return TextClamp.ellipsis(sb.toString(), maxChars);
    }
}
