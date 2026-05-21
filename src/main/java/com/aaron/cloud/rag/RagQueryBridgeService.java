package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.dto.RagVectorRecallHit;
import com.aaron.cloud.common.api.enums.RagRetrievalMode;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.api.ports.RagQueryPort;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.config.providers.VectorStoreProviderMode;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 鏍囧噯 RAG 妫€绱㈢紪鎺掞細浠?{@link RagRetrievalMode#MILVUS} 涓?{@link RagRetrievalMode#MILVUS_ES_HYBRID}锛涙煡璇笌鍏ュ簱鍏辩敤
 * {@link RagEmbeddingPort} 鍚戦噺鍖栧悗妫€绱?Milvus锛涙贩鍚堟ā寮忓悎骞?Elasticsearch 鍏抽敭璇嶇粨鏋滐紙鏃?MySQL 璇嶆硶鍙洖锛夈€? */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryBridgeService implements RagQueryPort {

    /**
     * 多知识库检索并行度：虚拟线程承载阻塞式 Milvus/ES I/O，同嵌入模型分组内共享一次 embed 后并行 search。
     */
    private static final Executor RAG_MULTI_KB_PARALLEL = Executors.newVirtualThreadPerTaskExecutor();

    private final AiProvidersProperties aiProvidersProperties;
    private final AiRagProperties aiRagProperties;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final RagChunkRepository ragChunkRepository;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final VectorStorePort vectorStorePort;
    private final ObjectProvider<ElasticsearchRagSearchClient> elasticsearchRagSearchClient;

    /** 与 {@code rag_knowledge_base.chat_vector_min_cosine_score} 列默认一致（旧行未迁移时兜底）。 */
    private static final double DEFAULT_KB_CHAT_VECTOR_MIN_COSINE = 0.65d;

    @Override
    public List<String> searchSnippets(Long tenantId, Long kbId, String query, int topK) {
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        long tid = tenantId == null ? 0L : tenantId;
        long kb = kbId == null ? 0L : kbId;
        RagRetrievalMode mode = aiRagProperties.resolvedRetrievalMode();
        List<String> out =
                switch (mode) {
                    case MILVUS -> searchMilvusSnippets(tid, kb, query, topK);
                    case MILVUS_ES_HYBRID -> searchHybridSnippets(tid, kb, query, topK);
                };
        log.info(
                "[知识库检索] 单库片段检索完成：租户 {}，知识库 {}，模式={}，最多 {} 条，查询 {} 字，命中 {} 条",
                tid,
                kb,
                RagQueryLogZh.mode(mode),
                topK,
                query == null ? 0 : query.length(),
                out.size());
        return out;
    }

    @Override
    public List<RagCitationHit> searchCitationHits(Long tenantId, Long kbId, String query, int topK) {
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        long tid = tenantId == null ? 0L : tenantId;
        long kb = kbId == null ? 0L : kbId;
        RagRetrievalMode mode = aiRagProperties.resolvedRetrievalMode();
        List<RagCitationHit> out =
                switch (mode) {
                    case MILVUS -> searchMilvusCitations(tid, kb, query, topK);
                    case MILVUS_ES_HYBRID -> searchHybridCitations(tid, kb, query, topK);
                };
        log.info(
                "[知识库检索] 单库可引用分片检索完成：租户 {}，知识库 {}，模式={}，最多 {} 条，查询 {} 字，命中 {} 条",
                tid,
                kb,
                RagQueryLogZh.mode(mode),
                topK,
                query == null ? 0 : query.length(),
                out.size());
        return out;
    }

    @Override
    public List<String> searchSnippetsAcrossKnowledgeBases(Long tenantId, List<Long> kbIds, String query, int topK) {
        long tid = tenantId == null ? 0L : tenantId;
        if (kbIds == null || kbIds.isEmpty()) {
            log.info("[知识库检索] 跳过多库片段检索：未指定知识库，租户 {}", tid);
            return List.of();
        }
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        RagRetrievalMode mode = aiRagProperties.resolvedRetrievalMode();
        List<String> out =
                switch (mode) {
                    case MILVUS -> mergeSnippetListsInKbOrder(
                            tid, kbIds, query, topK, this::searchMilvusSnippetsWithVec);
                    case MILVUS_ES_HYBRID -> mergeSnippetListsInKbOrder(
                            tid, kbIds, query, topK, this::searchHybridSnippetsWithMilvusVec);
                };
        log.info(
                "[知识库检索] 多库片段检索完成：租户 {}，知识库 {} 个，模式={}，最多 {} 条，查询 {} 字，合并后 {} 条",
                tid,
                kbIds.size(),
                RagQueryLogZh.mode(mode),
                topK,
                query == null ? 0 : query.length(),
                out.size());
        return out;
    }

    @Override
    public List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK) {
        long tid = tenantId == null ? 0L : tenantId;
        if (kbIds == null || kbIds.isEmpty()) {
            log.info("[知识库检索] 跳过多库可引用分片检索：未指定知识库，租户 {}", tid);
            return List.of();
        }
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        RagRetrievalMode mode = aiRagProperties.resolvedRetrievalMode();
        List<RagCitationHit> out =
                switch (mode) {
                    case MILVUS -> mergeCitationHitsInKbOrder(
                            tid, kbIds, query, topK, this::searchMilvusCitationsWithVec);
                    case MILVUS_ES_HYBRID -> mergeCitationHitsInKbOrder(
                            tid, kbIds, query, topK, this::searchHybridCitationsWithMilvusVec);
                };
        log.info(
                "[知识库检索] 多库可引用分片检索完成：租户 {}，知识库 {} 个，模式={}，最多 {} 条，查询 {} 字，合并后 {} 条",
                tid,
                kbIds.size(),
                RagQueryLogZh.mode(mode),
                topK,
                query == null ? 0 : query.length(),
                out.size());
        return out;
    }

    @FunctionalInterface
    private interface PerKbMilvusSnippetSearch {
        List<String> search(long tenantId, long kbId, String query, int topK, float[] milvusQueryVec);
    }

    @FunctionalInterface
    private interface PerKbMilvusCitationSearch {
        List<RagCitationHit> search(long tenantId, long kbId, String query, int topK, float[] milvusQueryVec);
    }

    /**
     * 多库：相同 {@code assigned_embedding_model_id} 只 embed 一次；各库 Milvus（及混合模式下各库 ES）并行；合并顺序与
     * {@code kbIds} 一致。
     */
    private List<String> mergeSnippetListsInKbOrder(
            long tenantId,
            List<Long> kbIds,
            String query,
            int topK,
            PerKbMilvusSnippetSearch perKbSearch) {
        String q = query == null ? "" : query;
        List<Long> orderedKbs = kbIds.stream().filter(Objects::nonNull).toList();
        if (orderedKbs.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> kbToMid = resolveKbToEmbeddingModelIdOrThrow(tenantId, orderedKbs, q);
        Map<Long, float[]> vecByMid = buildQueryVectorByEmbeddingModelId(tenantId, orderedKbs, kbToMid, q);
        if (orderedKbs.size() > 1) {
            log.debug(
                    "[知识库检索] 多库片段并行检索：租户 {}，知识库 {} 个，嵌入模型 {} 种",
                    tenantId,
                    orderedKbs.size(),
                    vecByMid.size());
        }
        Map<Long, List<String>> byKb =
                runPerKbSearchParallel(tenantId, orderedKbs, kbToMid, vecByMid, q, topK, perKbSearch);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Long kb : orderedKbs) {
            addUpTo(set, byKb.getOrDefault(kb, List.of()), topK);
            if (set.size() >= topK) {
                break;
            }
        }
        return truncateList(new ArrayList<>(set), topK);
    }

    private List<RagCitationHit> mergeCitationHitsInKbOrder(
            long tenantId,
            List<Long> kbIds,
            String query,
            int topK,
            PerKbMilvusCitationSearch perKbSearch) {
        String q = query == null ? "" : query;
        List<Long> orderedKbs = kbIds.stream().filter(Objects::nonNull).toList();
        if (orderedKbs.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> kbToMid = resolveKbToEmbeddingModelIdOrThrow(tenantId, orderedKbs, q);
        Map<Long, float[]> vecByMid = buildQueryVectorByEmbeddingModelId(tenantId, orderedKbs, kbToMid, q);
        if (orderedKbs.size() > 1) {
            log.debug(
                    "[知识库检索] 多库引用分片并行检索：租户 {}，知识库 {} 个，嵌入模型 {} 种",
                    tenantId,
                    orderedKbs.size(),
                    vecByMid.size());
        }
        Map<Long, List<RagCitationHit>> byKb =
                runPerKbSearchParallel(tenantId, orderedKbs, kbToMid, vecByMid, q, topK, perKbSearch);
        Map<Long, RagCitationHit> byChunk = new LinkedHashMap<>();
        for (Long kb : orderedKbs) {
            for (RagCitationHit h : byKb.getOrDefault(kb, List.of())) {
                byChunk.putIfAbsent(h.chunkId(), h);
                if (byChunk.size() >= topK) {
                    return new ArrayList<>(byChunk.values());
                }
            }
        }
        return new ArrayList<>(byChunk.values());
    }

    /**
     * 与原先逐库顺序调用一致：在列表顺序上首次遇到缺失库或未绑定向量模型时由 {@link RagEmbeddingPort#embed} 抛错。
     */
    private Map<Long, Long> resolveKbToEmbeddingModelIdOrThrow(long tenantId, List<Long> orderedKbs, String query) {
        Map<Long, Long> kbToMid = new LinkedHashMap<>();
        for (Long kb : orderedKbs) {
            RagKnowledgeBase row = ragKnowledgeBaseRepository.findByIdAndTenant(kb, tenantId);
            Long mid = row == null ? null : row.getAssignedEmbeddingModelId();
            if (mid == null) {
                ragEmbeddingPort.embed(tenantId, kb, query);
            }
            kbToMid.put(kb, Objects.requireNonNull(mid));
        }
        return kbToMid;
    }

    /** 每个不同的嵌入模型主键只调用一次上游 embed（以该组内在 kb 列表中首次出现的库为代表）。 */
    private Map<Long, float[]> buildQueryVectorByEmbeddingModelId(
            long tenantId, List<Long> orderedKbs, Map<Long, Long> kbToMid, String query) {
        Map<Long, float[]> vecByMid = new LinkedHashMap<>();
        for (Long kb : orderedKbs) {
            Long mid = kbToMid.get(kb);
            vecByMid.computeIfAbsent(mid, __ -> ragEmbeddingPort.embed(tenantId, kb, query));
        }
        return vecByMid;
    }

    private Map<Long, List<String>> runPerKbSearchParallel(
            long tenantId,
            List<Long> orderedKbs,
            Map<Long, Long> kbToMid,
            Map<Long, float[]> vecByMid,
            String query,
            int topK,
            PerKbMilvusSnippetSearch perKbSearch) {
        List<CompletableFuture<Map.Entry<Long, List<String>>>> futures = new ArrayList<>();
        for (Long kb : orderedKbs) {
            float[] vec = vecByMid.get(kbToMid.get(kb));
            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> Map.entry(kb, perKbSearch.search(tenantId, kb, query, topK, vec)),
                            RAG_MULTI_KB_PARALLEL));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<Long, List<String>>> f : futures) {
            Map.Entry<Long, List<String>> e = f.join();
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private Map<Long, List<RagCitationHit>> runPerKbSearchParallel(
            long tenantId,
            List<Long> orderedKbs,
            Map<Long, Long> kbToMid,
            Map<Long, float[]> vecByMid,
            String query,
            int topK,
            PerKbMilvusCitationSearch perKbSearch) {
        List<CompletableFuture<Map.Entry<Long, List<RagCitationHit>>>> futures = new ArrayList<>();
        for (Long kb : orderedKbs) {
            float[] vec = vecByMid.get(kbToMid.get(kb));
            futures.add(
                    CompletableFuture.supplyAsync(
                            () -> Map.entry(kb, perKbSearch.search(tenantId, kb, query, topK, vec)),
                            RAG_MULTI_KB_PARALLEL));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        Map<Long, List<RagCitationHit>> out = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<Long, List<RagCitationHit>>> f : futures) {
            Map.Entry<Long, List<RagCitationHit>> e = f.join();
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    private List<String> searchMilvusSnippets(long tenantId, long kbId, String query, int topK) {
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, query == null ? "" : query);
        return searchMilvusSnippetsWithVec(tenantId, kbId, vec, topK);
    }

    private List<String> searchMilvusSnippetsWithVec(long tenantId, long kbId, float[] queryVec, int topK) {
        List<RagVectorRecallHit> hits =
                vectorStorePort.searchVectors(tenantId, collectionName(kbId), queryVec, topK);
        return hitsToSnippets(tenantId, kbId, hits, topK);
    }

    private List<String> searchMilvusSnippetsWithVec(
            long tenantId, long kbId, String queryIgnored, int topK, float[] milvusQueryVec) {
        return searchMilvusSnippetsWithVec(tenantId, kbId, milvusQueryVec, topK);
    }

    private List<RagCitationHit> searchMilvusCitations(long tenantId, long kbId, String query, int topK) {
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, query == null ? "" : query);
        return searchMilvusCitationsWithVec(tenantId, kbId, vec, topK);
    }

    private List<RagCitationHit> searchMilvusCitationsWithVec(long tenantId, long kbId, float[] queryVec, int topK) {
        List<RagVectorRecallHit> hits =
                vectorStorePort.searchVectors(tenantId, collectionName(kbId), queryVec, topK);
        return hitsToCitations(tenantId, kbId, hits, topK);
    }

    /**
     * 管理端检索试跑诊断：解释 Milvus 有召回但被余弦阈值或分片关联过滤掉的情况。
     */
    public RagRetrievalTestDiagnostics buildRetrievalTestDiagnostics(
            long tenantId, long kbId, String query, int topK) {
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return new RagRetrievalTestDiagnostics(
                    0, 0, 0, 0d, "当前 ai.providers.vector-store 非 milvus，向量检索不可用。");
        }
        String q = query == null ? "" : query;
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, q);
        List<RagVectorRecallHit> hits =
                vectorStorePort.searchVectors(tenantId, collectionName(kbId), vec, topK);
        double minCos = resolveChatVectorMinCosineScore(tenantId, kbId);
        int afterCosine = 0;
        int resolvable = 0;
        for (RagVectorRecallHit hit : hits) {
            if (!passesMilvusCosineThreshold(hit.score(), minCos)) {
                continue;
            }
            afterCosine++;
            long chunkId = parseChunkRef(hit.embeddingRef());
            if (chunkId > 0
                    && ragChunkRepository.findCitationHitForKb(tenantId, kbId, chunkId).isPresent()) {
                resolvable++;
            }
        }
        String hint = buildRetrievalHint(hits.size(), afterCosine, resolvable, minCos);
        return new RagRetrievalTestDiagnostics(hits.size(), afterCosine, resolvable, minCos, hint);
    }

    private static String buildRetrievalHint(
            int milvusRecall, int afterCosine, int resolvable, double minCos) {
        if (milvusRecall == 0) {
            return "Milvus 未召回任何分片：请确认文档状态为「已发布」、入库时 Milvus 写入成功，或对本库执行「触发索引」。"
                    + " 混合检索模式下历史文档可能仅有 Milvus 无 ES 词条。";
        }
        if (afterCosine == 0) {
            return "Milvus 召回 "
                    + milvusRecall
                    + " 条，但均未达到本库对话向量阈值 "
                    + minCos
                    + "；可在知识库高级设置中调低 chat_vector_min_cosine_score。";
        }
        if (resolvable == 0) {
            return "通过阈值 "
                    + afterCosine
                    + " 条，但分片无法关联到本库可引用记录（可能 embedding_ref 与库内分片不一致）。";
        }
        return "Milvus 召回 "
                + milvusRecall
                + " 条，阈值过滤后 "
                + afterCosine
                + " 条，可引用 "
                + resolvable
                + " 条（阈值 "
                + minCos
                + "）。";
    }

    private List<RagCitationHit> searchMilvusCitationsWithVec(
            long tenantId, long kbId, String queryIgnored, int topK, float[] milvusQueryVec) {
        return searchMilvusCitationsWithVec(tenantId, kbId, milvusQueryVec, topK);
    }

    private List<String> searchHybridSnippets(long tenantId, long kbId, String query, int topK) {
        return searchHybridSnippets(tenantId, kbId, query, topK, null);
    }

    private List<String> searchHybridSnippets(
            long tenantId, long kbId, String query, int topK, float[] milvusQueryVecOrNull) {
        List<String> mil =
                milvusQueryVecOrNull != null
                        ? searchMilvusSnippetsWithVec(tenantId, kbId, milvusQueryVecOrNull, topK)
                        : searchMilvusSnippets(tenantId, kbId, query, topK);
        List<String> es = List.of();
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient != null) {
            es = esClient.searchContents(tenantId, kbId, query, topK);
        } else {
            log.debug("[知识库检索] 混合模式未配置 Elasticsearch 客户端，本库仅使用 Milvus 结果");
        }
        return mergeTwoListsDedupe(topK, mil, es);
    }

    private List<String> searchHybridSnippetsWithMilvusVec(
            long tenantId, long kbId, String query, int topK, float[] milvusQueryVec) {
        return searchHybridSnippets(tenantId, kbId, query, topK, milvusQueryVec);
    }

    private List<RagCitationHit> searchHybridCitations(long tenantId, long kbId, String query, int topK) {
        return searchHybridCitations(tenantId, kbId, query, topK, null);
    }

    private List<RagCitationHit> searchHybridCitations(
            long tenantId, long kbId, String query, int topK, float[] milvusQueryVecOrNull) {
        List<RagCitationHit> mil =
                milvusQueryVecOrNull != null
                        ? searchMilvusCitationsWithVec(tenantId, kbId, milvusQueryVecOrNull, topK)
                        : searchMilvusCitations(tenantId, kbId, query, topK);
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient == null) {
            return mil;
        }
        List<RagCitationHit> fromEs = esClient.searchCitationHits(tenantId, kbId, query, topK);
        Map<Long, RagCitationHit> merged = new LinkedHashMap<>();
        for (RagCitationHit h : mil) {
            merged.putIfAbsent(h.chunkId(), h);
        }
        for (RagCitationHit h : fromEs) {
            merged.putIfAbsent(h.chunkId(), h);
            if (merged.size() >= topK) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<RagCitationHit> searchHybridCitationsWithMilvusVec(
            long tenantId, long kbId, String query, int topK, float[] milvusQueryVec) {
        return searchHybridCitations(tenantId, kbId, query, topK, milvusQueryVec);
    }

    private List<String> hitsToSnippets(long tenantId, long kbId, List<RagVectorRecallHit> hits, int topK) {
        double minCos = resolveChatVectorMinCosineScore(tenantId, kbId);
        List<String> out = new ArrayList<>();
        for (RagVectorRecallHit hit : hits) {
            if (out.size() >= topK) {
                break;
            }
            if (!passesMilvusCosineThreshold(hit.score(), minCos)) {
                continue;
            }
            long chunkId = parseChunkRef(hit.embeddingRef());
            if (chunkId <= 0) {
                continue;
            }
            ragChunkRepository
                    .resolvePromptSnippetForKb(tenantId, kbId, chunkId)
                    .filter(p -> !p.isBlank())
                    .ifPresent(out::add);
        }
        return out;
    }

    private List<RagCitationHit> hitsToCitations(long tenantId, long kbId, List<RagVectorRecallHit> hits, int topK) {
        double minCos = resolveChatVectorMinCosineScore(tenantId, kbId);
        List<RagCitationHit> out = new ArrayList<>();
        for (RagVectorRecallHit hit : hits) {
            if (out.size() >= topK) {
                break;
            }
            if (!passesMilvusCosineThreshold(hit.score(), minCos)) {
                continue;
            }
            long chunkId = parseChunkRef(hit.embeddingRef());
            if (chunkId <= 0) {
                continue;
            }
            ragChunkRepository.findCitationHitForKb(tenantId, kbId, chunkId).ifPresent(out::add);
        }
        return out;
    }

    /**
     * 单库对话向量阈值：仅使用 {@code rag_knowledge_base.chat_vector_min_cosine_score}（管理端高级设置 / 库默认
     * 0.65）。实体字段为 null 或未查到知识库行时用固定兜底 0.65（与列默认一致）。
     * <p>多库检索（{@code *AcrossKnowledgeBases}）时对每个 {@code kbId} 分别检索并在本方法按库解析，互不混用；多库路径上对
     * Milvus/ES 并行调用，合并顺序仍与 {@code kbIds} 列表一致。
     */
    private double resolveChatVectorMinCosineScore(long tenantId, long kbId) {
        RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId);
        if (kb != null && kb.getChatVectorMinCosineScore() != null) {
            return kb.getChatVectorMinCosineScore();
        }
        return DEFAULT_KB_CHAT_VECTOR_MIN_COSINE;
    }

    /**
     * Milvus collection 使用 {@link io.milvus.v2.common.IndexParam.MetricType#COSINE} 时，SDK 返回的 {@code score}
     * 越大表示越相似；{@code minConfigured}≤0 或大于 1 时不做过滤。
     */
    private static boolean passesMilvusCosineThreshold(float score, double minConfigured) {
        if (minConfigured <= 0d || minConfigured > 1d) {
            return true;
        }
        return score >= minConfigured;
    }

    private static long parseChunkRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(ref.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String collectionName(long kbId) {
        return "kb_" + kbId;
    }

    private static void addUpTo(LinkedHashSet<String> set, List<String> list, int topK) {
        if (list == null) {
            return;
        }
        for (String s : list) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            set.add(t);
            if (set.size() >= topK) {
                return;
            }
        }
    }

    private static List<String> mergeTwoListsDedupe(int topK, List<String> first, List<String> second) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        addUpTo(set, first, topK);
        if (set.size() < topK) {
            addUpTo(set, second, topK);
        }
        return truncateList(new ArrayList<>(set), topK);
    }

    private static List<String> truncateList(List<String> in, int topK) {
        if (in.size() <= topK) {
            return in;
        }
        return new ArrayList<>(in.subList(0, topK));
    }
}
