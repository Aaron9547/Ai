package com.aaron.cloud.rag.quality;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.enums.eval.EvalRunStatus;
import com.aaron.cloud.common.api.enums.metering.LlmUsageScene;
import com.aaron.cloud.common.api.enums.rag.RagQualityAssessmentScope;
import com.aaron.cloud.common.chat.ChatConversationRepository;
import com.aaron.cloud.common.chat.ChatMessageRepository;
import com.aaron.cloud.common.chat.entity.ChatMessage;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.observability.ObservabilityAdminDisplaySupport;
import com.aaron.cloud.common.observability.ObservabilityEventSink;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagQualityAssessmentProperties;
import com.aaron.cloud.common.rag.RagQualityAssessmentRepository;
import com.aaron.cloud.common.rag.entity.RagChunk;
import com.aaron.cloud.common.rag.entity.RagQualityAssessment;
import com.aaron.cloud.common.security.AdminQueryTenantSupport;
import com.aaron.cloud.rag.RagQueryBridgeService;
import com.aaron.cloud.rag.RagRetrievalScoredHit;
import com.aaron.cloud.rag.RagRetrievalTestDiagnostics;
import com.aaron.cloud.rag.RagRetrievalTestSearchResult;
import com.aaron.cloud.rag.dto.RagQualityAdminDtos.RagQualityAssessmentSubmitRequest;
import com.aaron.cloud.rag.dto.RagQualityAdminDtos.RagQualityAssessmentView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RagQualityAssessmentService {

    private static final String CITATION_JUDGE_ZH =
            """
            你是 RAG 引用相关性裁判。给定用户问题与一条知识库片段，判断该片段是否与回答问题相关。
            只输出 JSON：{"relevant":true/false,"reason":"简短理由"}
            """;
    private static final String CITATION_JUDGE_EN =
            """
            You judge RAG citation relevance. Given a user question and a knowledge snippet, decide if the snippet is relevant.
            Output JSON only: {"relevant":true/false,"reason":"brief reason"}
            """;
    private static final String FAITHFULNESS_JUDGE_ZH =
            """
            你是答案忠实度裁判。给定用户问题、助手回答与知识库片段，判断回答是否被片段支撑，是否存在无依据陈述。
            只输出 JSON：{"score":0.0-1.0,"unsupportedClaims":["…"],"reason":"简短理由"}
            """;
    private static final String FAITHFULNESS_JUDGE_EN =
            """
            You judge answer faithfulness to citations. Given question, assistant answer and snippets, score groundedness 0-1.
            Output JSON only: {"score":0.0-1.0,"unsupportedClaims":["…"],"reason":"brief reason"}
            """;

    private final RagQualityAssessmentProperties properties;
    private final RagQualityAssessmentRepository repository;
    private final RagQueryBridgeService ragQueryBridgeService;
    private final RagChunkRepository ragChunkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ObservabilityAdminDisplaySupport displaySupport;
    private final SysLlmModelRepository llmModelRepository;
    private final RagQualityLlmJudge ragQualityLlmJudge;
    private final ObjectMapper objectMapper;
    private final Executor ragQualityExecutor;

    public RagQualityAssessmentService(
            RagQualityAssessmentProperties properties,
            RagQualityAssessmentRepository repository,
            RagQueryBridgeService ragQueryBridgeService,
            RagChunkRepository ragChunkRepository,
            ChatMessageRepository chatMessageRepository,
            ChatConversationRepository chatConversationRepository,
            ObservabilityAdminDisplaySupport displaySupport,
            SysLlmModelRepository llmModelRepository,
            RagQualityLlmJudge ragQualityLlmJudge,
            ObjectMapper objectMapper,
            @Qualifier("ragQualityExecutor") Executor ragQualityExecutor) {
        this.properties = properties;
        this.repository = repository;
        this.ragQueryBridgeService = ragQueryBridgeService;
        this.ragChunkRepository = ragChunkRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.displaySupport = displaySupport;
        this.llmModelRepository = llmModelRepository;
        this.ragQualityLlmJudge = ragQualityLlmJudge;
        this.objectMapper = objectMapper;
        this.ragQualityExecutor = ragQualityExecutor;
    }

    public RagQualityAssessmentView submit(RagQualityAssessmentSubmitRequest req, long adminUserId, Locale locale) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("RAG_QA_DISABLED");
        }
        long tenantId = TenantContextHolder.require().getTenantId();
        String runId = ObservabilityEventSink.newTraceId();
        RagQualityAssessment row = new RagQualityAssessment();
        row.setRunId(runId);
        row.setTenantId(tenantId);
        row.setScope(req.scope());
        row.setStatus(EvalRunStatus.QUEUED);
        row.setConversationId(req.conversationId());
        row.setUserMessageId(req.userMessageId());
        row.setAssistantMessageId(req.assistantMessageId());
        row.setKbId(req.kbId());
        row.setChunkId(req.chunkId());
        row.setQueryText(trimQuery(req.queryText()));
        row.setAssistantAnswer(req.assistantAnswer());
        row.setTriggeredByAdminId(adminUserId);
        row.setCreatedAt(LocalDateTime.now());
        repository.insert(row);
        Locale loc = locale != null ? locale : Locale.SIMPLIFIED_CHINESE;
        ragQualityExecutor.execute(() -> runAssessment(runId, loc));
        return toView(row);
    }

    public RagQualityAssessmentView getByRunId(String runId) {
        RagQualityAssessment row = repository.findByRunId(runId);
        if (row == null) {
            throw new IllegalArgumentException("RAG_QA_NOT_FOUND");
        }
        assertTenant(row.getTenantId());
        return toView(row);
    }

    public Page<RagQualityAssessment> pageForAdmin(
            Long filterTenantId,
            long page,
            long size,
            RagQualityAssessmentScope scope,
            String conversationKeyword,
            String queryKeyword,
            int days) {
        Long tid = AdminQueryTenantSupport.resolveAdminListTenantFilter(filterTenantId);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 180)));
        List<Long> conversationIds = null;
        if (conversationKeyword != null && !conversationKeyword.isBlank()) {
            conversationIds = chatConversationRepository.listIdsByTitleLike(tid, conversationKeyword, 200);
            if (conversationIds.isEmpty()) {
                Page<RagQualityAssessment> empty = Page.of(page, size);
                empty.setRecords(List.of());
                empty.setTotal(0);
                return empty;
            }
        }
        Page<RagQualityAssessment> result =
                repository.pageForAdmin(
                        tid, page, size, scope, null, null, conversationIds, queryKeyword, since);
        displaySupport.enrichQualityAssessments(result.getRecords());
        return result;
    }

    private void runAssessment(String runId, Locale locale) {
        RagQualityAssessment row = repository.findByRunId(runId);
        if (row == null) {
            return;
        }
        row.setStatus(EvalRunStatus.RUNNING);
        repository.updateById(row);
        try {
            ObjectNode result = objectMapper.createObjectNode();
            List<RagCitationHit> citations = resolveCitations(row);
            RecallOutcome recall = computeRecall(row, citations);
            result.set("recall", recall.node());
            row.setRecallHitRate(recall.rate());

            if (properties.isJudgeEnabled()
                    && row.getScope() == RagQualityAssessmentScope.MESSAGE_TURN
                    && row.getAssistantAnswer() != null
                    && !row.getAssistantAnswer().isBlank()
                    && !citations.isEmpty()) {
                ObjectNode citNode = judgeCitations(row, citations, locale);
                result.set("citationAccuracy", citNode);
                if (citNode.has("score")) {
                    row.setCitationAccuracy(BigDecimal.valueOf(citNode.get("score").asDouble()));
                }
                ObjectNode faithNode = judgeFaithfulness(row, row.getAssistantAnswer(), citations, locale);
                result.set("faithfulness", faithNode);
                if (faithNode.has("score")) {
                    row.setFaithfulnessScore(BigDecimal.valueOf(faithNode.get("score").asDouble()));
                }
            }

            row.setResultJson(objectMapper.writeValueAsString(result));
            row.setStatus(EvalRunStatus.SUCCEEDED);
            row.setFinishedAt(LocalDateTime.now());
            repository.updateById(row);
        } catch (Exception e) {
            log.warn("rag quality assessment failed runId={}", runId, e);
            row.setStatus(EvalRunStatus.FAILED);
            row.setErrorCode("RAG_QA_RUN_FAILED");
            String msg = e.getMessage();
            row.setErrorMessage(
                    msg == null ? "error" : msg.substring(0, Math.min(1024, msg.length())));
            row.setFinishedAt(LocalDateTime.now());
            repository.updateById(row);
        }
    }

    private List<RagCitationHit> resolveCitations(RagQualityAssessment row) {
        if (row.getAssistantMessageId() == null) {
            return List.of();
        }
        Optional<ChatMessage> msg = chatMessageRepository.findById(row.getAssistantMessageId(), row.getTenantId());
        if (msg.isEmpty() || msg.get().getMetaJson() == null || msg.get().getMetaJson().isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(msg.get().getMetaJson());
            if (!root.has("ragCitations") || !root.get("ragCitations").isArray()) {
                return List.of();
            }
            return objectMapper.convertValue(root.get("ragCitations"), new TypeReference<List<RagCitationHit>>() {});
        } catch (Exception e) {
            log.warn("parse ragCitations failed msgId={}", row.getAssistantMessageId(), e);
            return List.of();
        }
    }

    private RecallOutcome computeRecall(RagQualityAssessment row, List<RagCitationHit> citations) {
        String query = row.getQueryText();
        if (query == null || query.isBlank()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("citationRecallRate", 0);
            return new RecallOutcome(BigDecimal.ZERO, empty);
        }
        int topK = Math.max(1, properties.getTopK());
        Set<Long> targetChunks = new HashSet<>();
        if (row.getScope() == RagQualityAssessmentScope.CHUNK_QUERY && row.getChunkId() != null) {
            targetChunks.add(row.getChunkId());
        }
        for (RagCitationHit c : citations) {
            targetChunks.add(c.chunkId());
        }
        if (targetChunks.isEmpty()) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("citationRecallRate", 0);
            empty.put("topK", topK);
            return new RecallOutcome(BigDecimal.ZERO, empty);
        }
        Long kbId = row.getKbId();
        if (kbId == null && !citations.isEmpty()) {
            kbId = citations.get(0).kbId();
        }
        if (kbId == null) {
            ObjectNode empty = objectMapper.createObjectNode();
            empty.put("hintCode", "RAG_QA_NO_KB");
            return new RecallOutcome(BigDecimal.ZERO, empty);
        }
        RagRetrievalTestSearchResult search =
                ragQueryBridgeService.searchForRetrievalTest(row.getTenantId(), kbId, query, topK);
        List<RagRetrievalScoredHit> hits = search.hits();
        int inTop = 0;
        ArrayNode hitArr = objectMapper.createArrayNode();
        int rank = 1;
        for (RagRetrievalScoredHit h : hits) {
            long cid = h.citation().chunkId();
            boolean inCited = targetChunks.contains(cid);
            if (inCited) {
                inTop++;
            }
            ObjectNode item = objectMapper.createObjectNode();
            item.put("chunkId", cid);
            item.put("rank", rank++);
            item.put("inActualCitations", inCited);
            if (h.vectorSimilarity() != null) {
                item.put("vectorSimilarity", h.vectorSimilarity());
            }
            if (h.keywordScore() != null) {
                item.put("keywordScore", h.keywordScore());
            }
            if (h.source() != null) {
                item.put("hitSource", h.source().getCode());
            }
            hitArr.add(item);
        }
        double rate = (double) inTop / targetChunks.size();
        BigDecimal rateBd = BigDecimal.valueOf(rate).setScale(4, RoundingMode.HALF_UP);
        ObjectNode recall = objectMapper.createObjectNode();
        recall.put("citationRecallRate", rate);
        recall.put("citedInTopK", inTop);
        recall.put("totalCited", targetChunks.size());
        recall.put("topK", topK);
        recall.set("retrievalHits", hitArr);
        RagRetrievalTestDiagnostics diag = search.diagnostics();
        if (diag != null) {
            ObjectNode d = objectMapper.createObjectNode();
            d.put("milvusRecallCount", diag.milvusRecallCount());
            d.put("afterCosineThresholdCount", diag.afterCosineThresholdCount());
            d.put("resolvableChunkCount", diag.resolvableChunkCount());
            d.put("minCosineThreshold", diag.minCosineThreshold());
            d.put("maxMilvusSimilarity", diag.maxMilvusSimilarity());
            recall.set("diagnostics", d);
        }
        return new RecallOutcome(rateBd, recall);
    }

    private ObjectNode judgeCitations(RagQualityAssessment row, List<RagCitationHit> citations, Locale locale)
            throws Exception {
        Optional<SysLlmModel> model = llmModelRepository.pickDefaultLanguageModel(row.getTenantId());
        if (model.isEmpty()) {
            ObjectNode skip = objectMapper.createObjectNode();
            skip.put("hintCode", "RAG_QA_NO_LLM");
            return skip;
        }
        LlmModelKindPolicy.assertLanguageModelForChatStream(model.get());
        String sys = locale.getLanguage().startsWith("en") ? CITATION_JUDGE_EN : CITATION_JUDGE_ZH;
        int relevant = 0;
        ArrayNode items = objectMapper.createArrayNode();
        for (RagCitationHit c : citations) {
            String snippet = chunkText(row.getTenantId(), c.chunkId(), c.contentPreview());
            String userPayload = "question: " + row.getQueryText() + "\nsnippet: " + snippet;
            String raw =
                    ragQualityLlmJudge.invoke(
                            row.getTenantId(), model.get(), sys, userPayload, LlmUsageScene.CHAT.getCode());
            JsonNode parsed = parseJsonObject(raw);
            boolean rel = parsed.path("relevant").asBoolean(false);
            if (rel) {
                relevant++;
            }
            ObjectNode item = objectMapper.createObjectNode();
            item.put("chunkId", c.chunkId());
            item.put("relevant", rel);
            item.put("judgeReason", parsed.path("reason").asText(""));
            items.add(item);
        }
        double score = (double) relevant / citations.size();
        ObjectNode out = objectMapper.createObjectNode();
        out.put("score", score);
        out.set("items", items);
        return out;
    }

    private ObjectNode judgeFaithfulness(
            RagQualityAssessment row, String answer, List<RagCitationHit> citations, Locale locale)
            throws Exception {
        Optional<SysLlmModel> model = llmModelRepository.pickDefaultLanguageModel(row.getTenantId());
        if (model.isEmpty()) {
            ObjectNode skip = objectMapper.createObjectNode();
            skip.put("hintCode", "RAG_QA_NO_LLM");
            return skip;
        }
        StringBuilder cites = new StringBuilder();
        for (RagCitationHit c : citations) {
            cites.append("\n---\n").append(chunkText(row.getTenantId(), c.chunkId(), c.contentPreview()));
        }
        String sys = locale.getLanguage().startsWith("en") ? FAITHFULNESS_JUDGE_EN : FAITHFULNESS_JUDGE_ZH;
        String userPayload =
                "question: " + row.getQueryText() + "\nanswer: " + answer + "\ncitations:" + cites;
        String raw =
                ragQualityLlmJudge.invoke(
                        row.getTenantId(), model.get(), sys, userPayload, LlmUsageScene.CHAT.getCode());
        JsonNode parsed = parseJsonObject(raw);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("score", parsed.path("score").asDouble(0d));
        out.put("judgeReason", parsed.path("reason").asText(""));
        if (parsed.has("unsupportedClaims") && parsed.get("unsupportedClaims").isArray()) {
            out.set("unsupportedClaims", parsed.get("unsupportedClaims"));
        }
        return out;
    }

    private String chunkText(long tenantId, long chunkId, String previewFallback) {
        RagChunk ch = ragChunkRepository.findByIdAndTenant(chunkId, tenantId);
        if (ch == null) {
            return previewFallback == null ? "" : previewFallback;
        }
        if (ch.getContent() != null && ch.getContent().length() > 400) {
            return ch.getContent().substring(0, 400) + "…";
        }
        return ch.getContent() == null ? (previewFallback == null ? "" : previewFallback) : ch.getContent();
    }

    private JsonNode parseJsonObject(String raw) throws Exception {
        if (raw == null) {
            return objectMapper.createObjectNode();
        }
        String t = raw.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1);
        }
        return objectMapper.readTree(t);
    }

    private static String trimQuery(String q) {
        if (q == null) {
            return null;
        }
        return q.length() <= 1024 ? q : q.substring(0, 1024);
    }

    private void assertTenant(long rowTenantId) {
        long tid = TenantContextHolder.require().getTenantId();
        if (rowTenantId != tid) {
            Long filter = AdminQueryTenantSupport.resolveAdminListTenantFilter(null);
            if (filter == null || filter != rowTenantId) {
                throw new IllegalArgumentException("forbidden");
            }
        }
    }

    private RagQualityAssessmentView toView(RagQualityAssessment row) {
        displaySupport.enrichQualityAssessments(List.of(row));
        return new RagQualityAssessmentView(
                row.getRunId(),
                row.getScope(),
                row.getStatus(),
                row.getConversationId(),
                row.getUserMessageId(),
                row.getAssistantMessageId(),
                row.getKbId(),
                row.getChunkId(),
                row.getQueryText(),
                row.getRecallHitRate(),
                row.getCitationAccuracy(),
                row.getFaithfulnessScore(),
                row.getResultJson(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getCreatedAt(),
                row.getFinishedAt(),
                row.getConversationTitle(),
                row.getKbName(),
                row.getChunkLabel());
    }

    private record RecallOutcome(BigDecimal rate, ObjectNode node) {}
}
