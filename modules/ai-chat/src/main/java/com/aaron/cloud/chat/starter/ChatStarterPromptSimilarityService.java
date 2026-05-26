package com.aaron.cloud.chat.starter;

import com.aaron.cloud.chat.dto.ChatStarterPromptDtos;
import com.aaron.cloud.chat.websearch.cache.WebSearchVectorSimilarity;
import com.aaron.cloud.common.api.enums.chat.ChatStarterPromptScene;
import com.aaron.cloud.common.chat.ChatStarterPromptRepository;
import com.aaron.cloud.common.chat.entity.ChatStarterPrompt;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 从运营池按与当前对话上下文的相似度挑选「猜你想问」/追问推荐（本地优先，可走 VECTOR 嵌入重排）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStarterPromptSimilarityService {

    private static final double LEXICAL_MIN_SCORE = 0.08;
    private static final double SEMANTIC_MIN_SCORE = 0.78;
    private static final int LEXICAL_PREFILTER = 48;
    private static final int SEMANTIC_RERANK_CAP = 10;

    private final ChatStarterPromptRepository promptRepository;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final RagEmbeddingPort ragEmbeddingPort;

    public Optional<ChatStarterPromptDtos.StarterPromptListView> pickForConversation(
            long tenantId, String userQ, String assistantText, int limit) {
        String context = buildContext(userQ, assistantText);
        if (context.isBlank()) {
            return Optional.empty();
        }
        int cap = Math.max(1, Math.min(limit, 6));
        List<ChatStarterPrompt> pool = loadPool(tenantId);
        if (pool.isEmpty()) {
            return Optional.empty();
        }

        Set<String> ctxTokens = tokenize(context);
        List<Scored> scored = new ArrayList<>();
        for (ChatStarterPrompt p : pool) {
            String text = p.getPromptText();
            if (text == null || text.isBlank()) {
                continue;
            }
            double lex = lexicalScore(ctxTokens, tokenize(text));
            if (lex >= LEXICAL_MIN_SCORE) {
                scored.add(new Scored(p, lex));
            }
        }
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        scored.sort(Comparator.comparingDouble(Scored::lexical).reversed());
        List<Scored> candidates = scored.subList(0, Math.min(LEXICAL_PREFILTER, scored.size()));
        rerankWithEmbeddingIfConfigured(tenantId, context, candidates);

        candidates.sort(Comparator.comparingDouble(Scored::finalScore).reversed());
        List<ChatStarterPromptDtos.StarterPromptItem> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Scored s : candidates) {
            if (s.prompt().getId() != null && !seen.add(s.prompt().getId())) {
                continue;
            }
            items.add(toItem(s.prompt()));
            if (items.size() >= cap) {
                break;
            }
        }
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ChatStarterPromptDtos.StarterPromptListView(items, false));
    }

    private List<ChatStarterPrompt> loadPool(long tenantId) {
        var today = BeijingTime.today();
        List<ChatStarterPrompt> merged = new ArrayList<>();
        merged.addAll(
                promptRepository.listEnabledForRuntime(
                        tenantId, ChatStarterPromptScene.FOLLOW_UP, today));
        merged.addAll(
                promptRepository.listEnabledForRuntime(
                        tenantId, ChatStarterPromptScene.WEB_KNOWLEDGE, today));
        if (!merged.isEmpty()) {
            return merged;
        }
        return promptRepository.listEnabledForRuntime(
                tenantId, ChatStarterPromptScene.EMPTY, today);
    }

    private void rerankWithEmbeddingIfConfigured(long tenantId, String context, List<Scored> candidates) {
        Optional<Long> vectorModelId =
                tenantRuntimeSettingApplicationService.memoryEmbeddingVectorModelId(tenantId);
        if (vectorModelId.isEmpty()) {
            for (Scored s : candidates) {
                s.semanticScore = s.lexical;
            }
            return;
        }
        float[] ctxVec;
        try {
            ctxVec =
                    ragEmbeddingPort.embedByVectorModelIdOrHash(
                            tenantId, vectorModelId.get(), context);
        } catch (Exception ex) {
            log.debug("[推荐问题] 上下文向量化失败 tenantId={}", tenantId, ex);
            for (Scored s : candidates) {
                s.semanticScore = s.lexical;
            }
            return;
        }
        if (ctxVec == null || ctxVec.length == 0) {
            for (Scored s : candidates) {
                s.semanticScore = s.lexical;
            }
            return;
        }
        int n = Math.min(SEMANTIC_RERANK_CAP, candidates.size());
        for (int i = 0; i < n; i++) {
            Scored s = candidates.get(i);
            String text = s.prompt().getPromptText();
            if (text == null || text.isBlank()) {
                s.semanticScore = s.lexical;
                continue;
            }
            try {
                float[] pVec =
                        ragEmbeddingPort.embedByVectorModelIdOrHash(
                                tenantId, vectorModelId.get(), text);
                double cos = WebSearchVectorSimilarity.cosine(ctxVec, pVec);
                if (cos >= SEMANTIC_MIN_SCORE) {
                    s.semanticScore = cos;
                } else {
                    s.semanticScore = s.lexical * 0.5;
                }
            } catch (Exception ex) {
                s.semanticScore = s.lexical;
            }
        }
        for (int i = n; i < candidates.size(); i++) {
            candidates.get(i).semanticScore = candidates.get(i).lexical;
        }
    }

    private static String buildContext(String userQ, String assistantText) {
        String u = userQ == null ? "" : userQ.trim();
        String a = assistantText == null ? "" : assistantText.trim();
        if (a.length() > 1200) {
            a = a.substring(0, 1200);
        }
        if (u.isEmpty() && a.isEmpty()) {
            return "";
        }
        if (u.isEmpty()) {
            return a;
        }
        if (a.isEmpty()) {
            return u;
        }
        return u + "\n" + a;
    }

    private static Set<String> tokenize(String text) {
        String n = text.replaceAll("\\s+", "").toLowerCase();
        Set<String> tokens = new HashSet<>();
        if (n.isEmpty()) {
            return tokens;
        }
        for (int i = 0; i < n.length(); i++) {
            tokens.add(String.valueOf(n.charAt(i)));
            if (i + 1 < n.length()) {
                tokens.add(n.substring(i, i + 2));
            }
        }
        return tokens;
    }

    private static double lexicalScore(Set<String> ctx, Set<String> prompt) {
        if (ctx.isEmpty() || prompt.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String t : prompt) {
            if (ctx.contains(t)) {
                inter++;
            }
        }
        return (double) inter / Math.max(ctx.size(), prompt.size());
    }

    private static ChatStarterPromptDtos.StarterPromptItem toItem(ChatStarterPrompt p) {
        return new ChatStarterPromptDtos.StarterPromptItem(
                p.getId(),
                p.getPromptText(),
                p.getSource() == null ? "MANUAL" : p.getSource().getCode());
    }

    private static final class Scored {
        private final ChatStarterPrompt prompt;
        private final double lexical;
        private double semanticScore;

        private Scored(ChatStarterPrompt prompt, double lexical) {
            this.prompt = prompt;
            this.lexical = lexical;
            this.semanticScore = lexical;
        }

        private ChatStarterPrompt prompt() {
            return prompt;
        }

        private double lexical() {
            return lexical;
        }

        private double finalScore() {
            return semanticScore;
        }
    }
}
