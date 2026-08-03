package com.aaron.cloud.chat.rag;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
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
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    public record GateOutcome(boolean accepted, double similarity) {}

    /**
     * @param embeddingKbId 参与对话检索的首个知识库 id；有值时沿用该库 VECTOR 模型，否则回退租户记忆嵌入模型 / hash 占位
     */
    public GateOutcome evaluate(
            long tenantId,
            Long embeddingKbId,
            List<ModelChatRequest.MessageTurn> history,
            String rewrittenQuery,
            RagRetrievalTuningRuntime tuning) {
        String context = buildContextText(history, tuning.resolvedRewriteContextMaxChars());
        String rewritten = rewrittenQuery == null ? "" : rewrittenQuery.trim();
        if (context.isBlank() || rewritten.isBlank()) {
            return new GateOutcome(true, 1.0d);
        }
        float[] ctxVec = embedForGate(tenantId, embeddingKbId, context);
        float[] qVec = embedForGate(tenantId, embeddingKbId, rewritten);
        double sim = WebSearchVectorSimilarity.cosine(ctxVec, qVec);
        double min = tuning.resolvedRewriteSemanticMinSimilarity();
        return new GateOutcome(sim >= min, sim);
    }

    private float[] embedForGate(long tenantId, Long embeddingKbId, String text) {
        if (embeddingKbId != null && embeddingKbId > 0L) {
            return ragEmbeddingPort.embed(tenantId, embeddingKbId, text);
        }
        Long modelId =
                tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(tenantId).orElse(null);
        return ragEmbeddingPort.embedByVectorModelIdOrHash(tenantId, modelId, text);
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
