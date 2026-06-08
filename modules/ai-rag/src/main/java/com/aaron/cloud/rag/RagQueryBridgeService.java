package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.dto.RagVectorRecallHit;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalHitSource;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile;
import com.aaron.cloud.common.api.ports.RagEmbeddingPort;
import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.api.enums.infra.VectorStoreProviderMode;
import com.aaron.cloud.rag.runtime.TenantRagRuntimeResolver;
import com.aaron.cloud.rag.ltr.RagRetrievalLtrService;
import com.aaron.cloud.common.rag.RagChunkRepository;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
public class RagQueryBridgeService {

    /**
     * 多知识库检索并行度：虚拟线程承载阻塞式 Milvus/ES I/O，同嵌入模型分组内共享一次 embed 后并行 search。
     */
    private static final Executor RAG_MULTI_KB_PARALLEL = Executors.newVirtualThreadPerTaskExecutor();

    private final AiProvidersProperties aiProvidersProperties;
    private final TenantRagRuntimeResolver tenantRagRuntimeResolver;
    private final RagEmbeddingPort ragEmbeddingPort;
    private final RagChunkRepository ragChunkRepository;
    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final VectorStorePort vectorStorePort;
    private final ObjectProvider<ElasticsearchRagSearchClient> elasticsearchRagSearchClient;
    private final RagRetrievalLtrService ragRetrievalLtrService;

    /** 与 {@code rag_knowledge_base.chat_vector_min_cosine_score} 列默认一致（旧行未迁移时兜底）。 */
    private static final double DEFAULT_KB_CHAT_VECTOR_MIN_COSINE = 0.65d;

    public List<String> searchSnippets(Long tenantId, Long kbId, String query, int topK) {
        return searchSnippets(tenantId, kbId, query, topK, null);
    }

    public List<String> searchSnippets(
            Long tenantId, Long kbId, String query, int topK, RagRetrievalProfile profile) {
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        long tid = tenantId == null ? 0L : tenantId;
        long kb = kbId == null ? 0L : kbId;
        RagRetrievalMode mode = resolveEffectiveMode(tid, profile);
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

    public List<RagCitationHit> searchCitationHits(Long tenantId, Long kbId, String query, int topK) {
        return searchCitationHits(tenantId, kbId, query, topK, null);
    }

    public List<RagCitationHit> searchCitationHits(
            Long tenantId, Long kbId, String query, int topK, RagRetrievalProfile profile) {
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        long tid = tenantId == null ? 0L : tenantId;
        long kb = kbId == null ? 0L : kbId;
        RagRetrievalMode mode = resolveEffectiveMode(tid, profile);
        List<RagCitationHit> out =
                switch (mode) {
                    case MILVUS -> searchMilvusCitations(tid, kb, query, topK);
                    case MILVUS_ES_HYBRID ->
                            searchHybridCitationsWithOptionalLtr(tid, kb, query, topK, profile);
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

    public List<String> searchSnippetsAcrossKnowledgeBases(Long tenantId, List<Long> kbIds, String query, int topK) {
        return searchSnippetsAcrossKnowledgeBases(tenantId, kbIds, query, topK, null);
    }

    public List<String> searchSnippetsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK, RagRetrievalProfile profile) {
        long tid = tenantId == null ? 0L : tenantId;
        if (kbIds == null || kbIds.isEmpty()) {
            log.info("[知识库检索] 跳过多库片段检索：未指定知识库，租户 {}", tid);
            return List.of();
        }
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        RagRetrievalMode mode = resolveEffectiveMode(tid, profile);
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

    public List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK) {
        return searchCitationHitsAcrossKnowledgeBases(tenantId, kbIds, query, topK, null);
    }

    public List<RagCitationHit> searchCitationHitsAcrossKnowledgeBases(
            Long tenantId, List<Long> kbIds, String query, int topK, RagRetrievalProfile profile) {
        long tid = tenantId == null ? 0L : tenantId;
        if (kbIds == null || kbIds.isEmpty()) {
            log.info("[知识库检索] 跳过多库可引用分片检索：未指定知识库，租户 {}", tid);
            return List.of();
        }
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        RagRetrievalMode mode = resolveEffectiveMode(tid, profile);
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
     * 管理端检索试跑：单次 embed + Milvus search，返回带相似度的命中、片段与诊断（与对话 RAG 同过滤规则）。
     */
    public RagRetrievalTestSearchResult searchForRetrievalTest(
            long tenantId, long kbId, String query, int topK) {
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            RagRetrievalTestDiagnostics diag =
                    new RagRetrievalTestDiagnostics(
                            0,
                            0,
                            0,
                            0d,
                            0d,
                            "当前 ai.providers.vector-store 非 milvus，向量检索不可用。");
            return new RagRetrievalTestSearchResult(List.of(), List.of(), diag);
        }
        String q = query == null ? "" : query;
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, q);
        List<RagVectorRecallHit> milvusHits =
                vectorStorePort.searchVectors(tenantId, collectionName(kbId), vec, topK);
        double minCos = resolveChatVectorMinCosineScore(tenantId, kbId);
        double maxSim = maxMilvusScore(milvusHits);
        logRetrievalTestMilvusScores(tenantId, kbId, q, topK, minCos, maxSim, milvusHits);

        RagRetrievalMode mode = tenantRagRuntimeResolver.resolveRetrievalMode(tenantId);
        List<RagRetrievalScoredHit> scoredHits =
                switch (mode) {
                    case MILVUS -> milvusHitsToScoredCitations(tenantId, kbId, milvusHits, topK, minCos);
                    case MILVUS_ES_HYBRID ->
                            searchHybridScoredCitations(tenantId, kbId, q, topK, milvusHits, minCos);
                };
        List<String> snippets = milvusHitsToSnippets(tenantId, kbId, milvusHits, topK, minCos);
        if (mode == RagRetrievalMode.MILVUS_ES_HYBRID) {
            if (!snippets.isEmpty()) {
                snippets = mergeHybridSnippetsForTest(tenantId, kbId, q, topK, snippets);
            } else {
                snippets = esKeywordSnippetsOnly(tenantId, kbId, q, topK);
            }
            logRetrievalTestHybridEsBranch(tenantId, kbId, scoredHits, milvusHits, minCos, maxSim);
        }
        RagRetrievalTestDiagnostics diag =
                buildRetrievalTestDiagnosticsFromMilvusHits(
                        tenantId, kbId, milvusHits, minCos, maxSim, scoredHits);
        log.info(
                "[知识库检索试跑] 完成 tenantId={} kbId={} mode={} topK={} queryLen={} milvusRecall={} maxSim={} threshold={} finalHits={}",
                tenantId,
                kbId,
                RagQueryLogZh.mode(mode),
                topK,
                q.length(),
                milvusHits.size(),
                formatSimilarity(maxSim),
                minCos,
                scoredHits.size());
        return new RagRetrievalTestSearchResult(scoredHits, snippets, diag);
    }

    /** @deprecated 请使用 {@link #searchForRetrievalTest}，避免重复 Milvus 检索。 */
    @Deprecated
    public RagRetrievalTestDiagnostics buildRetrievalTestDiagnostics(
            long tenantId, long kbId, String query, int topK) {
        return searchForRetrievalTest(tenantId, kbId, query, topK).diagnostics();
    }

    private RagRetrievalTestDiagnostics buildRetrievalTestDiagnosticsFromMilvusHits(
            long tenantId,
            long kbId,
            List<RagVectorRecallHit> hits,
            double minCos,
            double maxSim,
            List<RagRetrievalScoredHit> finalHits) {
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
        String hint =
                buildRetrievalHint(hits.size(), afterCosine, resolvable, minCos, maxSim, finalHits);
        return new RagRetrievalTestDiagnostics(
                hits.size(), afterCosine, resolvable, minCos, maxSim, hint);
    }

    private void logRetrievalTestHybridEsBranch(
            long tenantId,
            long kbId,
            List<RagRetrievalScoredHit> finalHits,
            List<RagVectorRecallHit> milvusHits,
            double minCos,
            double maxSim) {
        long milvusHitCount =
                finalHits.stream().filter(h -> h.source() == RagRetrievalHitSource.MILVUS).count();
        long esHitCount =
                finalHits.stream().filter(h -> h.source() == RagRetrievalHitSource.ES).count();
        if (milvusHitCount == 0 && !milvusHits.isEmpty()) {
            if (esHitCount > 0) {
                log.info(
                        "[知识库检索试跑] 向量均未过阈值 {}（最高 {}），已启用 ES 关键词兜底 {} 条",
                        minCos,
                        formatSimilarity(maxSim),
                        esHitCount);
            } else {
                log.info(
                        "[知识库检索试跑] Milvus 原始召回 {} 条均未过阈值 {}（最高相似度 {}），ES 关键词兜底无命中",
                        milvusHits.size(),
                        minCos,
                        formatSimilarity(maxSim));
            }
            if (esHitCount == 0) {
                return;
            }
        }
        if (esHitCount > 0 && milvusHitCount > 0) {
            log.info(
                    "[知识库检索试跑] 混合命中：Milvus {} 条 + ES {} 条（向量过阈值后 ES 关键词补充）",
                    milvusHitCount,
                    esHitCount);
        }
        if (esHitCount > 0) {
            int rank = 0;
            for (RagRetrievalScoredHit h : finalHits) {
                if (h.source() != RagRetrievalHitSource.ES) {
                    continue;
                }
                rank++;
                var c = h.citation();
                log.info(
                        "[知识库检索试跑] ES#{} chunkId={} bm25={} doc={}#{}",
                        rank,
                        c.chunkId(),
                        h.keywordScore() == null ? emDash() : formatSimilarity(h.keywordScore()),
                        c.documentTitle() == null ? "" : c.documentTitle(),
                        c.chunkSeq() + 1);
            }
        }
    }

    private void logRetrievalTestMilvusScores(
            long tenantId,
            long kbId,
            String query,
            int topK,
            double minCos,
            double maxSim,
            List<RagVectorRecallHit> milvusHits) {
        log.info(
                "[知识库检索试跑] Milvus 原始召回 tenantId={} kbId={} topK={} queryLen={} recallCount={} maxSimilarity={} threshold={} queryPreview={}",
                tenantId,
                kbId,
                topK,
                query == null ? 0 : query.length(),
                milvusHits.size(),
                formatSimilarity(maxSim),
                minCos,
                previewForLog(query, 80));
        int rank = 0;
        for (RagVectorRecallHit hit : milvusHits) {
            rank++;
            long chunkId = parseChunkRef(hit.embeddingRef());
            String docHint = "";
            if (chunkId > 0) {
                docHint =
                        ragChunkRepository
                                .findCitationHitForKb(tenantId, kbId, chunkId)
                                .map(
                                        c ->
                                                (c.documentTitle() == null ? "" : c.documentTitle())
                                                        + "#"
                                                        + (c.chunkSeq() + 1))
                                .orElse("chunkId=" + chunkId + "(未关联)");
            }
            log.info(
                    "[知识库检索试跑] Milvus#{} chunkRef={} similarity={} passedThreshold={} doc={}",
                    rank,
                    hit.embeddingRef(),
                    formatSimilarity(hit.score()),
                    passesMilvusCosineThreshold(hit.score(), minCos),
                    docHint.isEmpty() ? emDash() : docHint);
        }
    }

    private static String emDash() {
        return "—";
    }

    private static String previewForLog(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String t = text.trim().replace('\n', ' ');
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "…";
    }

    private static double maxMilvusScore(List<RagVectorRecallHit> hits) {
        float max = 0f;
        for (RagVectorRecallHit h : hits) {
            if (h.score() > max) {
                max = h.score();
            }
        }
        return max;
    }

    /**
     * 同一次 Milvus topK 召回的 chunkId → 余弦分（含未过阈值的原始分，便于混合检索试跑对照 ES 命中）。
     */
    private static Map<Long, Double> milvusRawScoreByChunkId(List<RagVectorRecallHit> hits) {
        Map<Long, Double> map = new LinkedHashMap<>();
        for (RagVectorRecallHit hit : hits) {
            long chunkId = parseChunkRef(hit.embeddingRef());
            if (chunkId <= 0) {
                continue;
            }
            map.putIfAbsent(chunkId, (double) hit.score());
        }
        return map;
    }

    private static String formatSimilarity(double score) {
        return String.format(java.util.Locale.ROOT, "%.4f", score);
    }

    private static String formatSimilarity(float score) {
        return formatSimilarity((double) score);
    }

    private List<RagRetrievalScoredHit> milvusHitsToScoredCitations(
            long tenantId, long kbId, List<RagVectorRecallHit> hits, int topK, double minCos) {
        return milvusHitsToScoredCitations(tenantId, kbId, hits, topK, minCos, false);
    }

    /** 混合检索：同一 {@code documentId} 只保留一条（Milvus 优先顺序），避免 ES 同文档多分片占满 topK。 */
    private List<RagRetrievalScoredHit> milvusHitsToScoredCitationsHybrid(
            long tenantId, long kbId, List<RagVectorRecallHit> hits, int topK, double minCos) {
        return milvusHitsToScoredCitations(tenantId, kbId, hits, topK, minCos, true);
    }

    private List<RagRetrievalScoredHit> milvusHitsToScoredCitations(
            long tenantId,
            long kbId,
            List<RagVectorRecallHit> hits,
            int topK,
            double minCos,
            boolean oneChunkPerDocument) {
        List<RagRetrievalScoredHit> out = new ArrayList<>();
        Set<Long> seenDocIds = oneChunkPerDocument ? new LinkedHashSet<>() : Set.of();
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
                    .findCitationHitForKb(tenantId, kbId, chunkId)
                    .ifPresent(
                            c -> {
                                if (oneChunkPerDocument && seenDocIds.contains(c.documentId())) {
                                    return;
                                }
                                if (oneChunkPerDocument) {
                                    seenDocIds.add(c.documentId());
                                }
                                out.add(
                                        new RagRetrievalScoredHit(
                                                c,
                                                RagRetrievalHitSource.MILVUS,
                                                (double) hit.score(),
                                                null));
                            });
        }
        return out;
    }

    private List<RagRetrievalScoredHit> searchHybridScoredCitations(
            long tenantId,
            long kbId,
            String query,
            int topK,
            List<RagVectorRecallHit> milvusHits,
            double minCos) {
        int candidateK = ragRetrievalLtrService.candidatePoolSize(tenantId, topK);
        List<RagRetrievalScoredHit> mil =
                milvusHitsToScoredCitationsHybrid(tenantId, kbId, milvusHits, candidateK, minCos);
        List<RagRetrievalScoredHit> pool;
        if (mil.isEmpty()) {
            pool = esKeywordScoredHitsOnly(tenantId, kbId, query, candidateK);
        } else {
            Map<Long, Double> milvusRawByChunk = milvusRawScoreByChunkId(milvusHits);
            Map<Long, RagRetrievalScoredHit> merged = new LinkedHashMap<>();
            for (RagRetrievalScoredHit h : mil) {
                merged.putIfAbsent(h.citation().chunkId(), h);
            }
            mergeEsScoredHitsUnlimited(merged, tenantId, kbId, query, candidateK, milvusRawByChunk);
            pool = new ArrayList<>(merged.values());
        }
        List<RagRetrievalScoredHit> ranked =
                ragRetrievalLtrService.rerankIfEnabled(tenantId, query, pool);
        return applyHybridDocumentCap(ranked, topK);
    }

    private List<RagRetrievalScoredHit> applyHybridDocumentCap(
            List<RagRetrievalScoredHit> ranked, int topK) {
        List<RagRetrievalScoredHit> out = new ArrayList<>();
        Set<Long> seenDocIds = new LinkedHashSet<>();
        for (RagRetrievalScoredHit h : ranked) {
            if (out.size() >= topK) {
                break;
            }
            long docId = h.citation().documentId();
            if (seenDocIds.contains(docId)) {
                continue;
            }
            seenDocIds.add(docId);
            out.add(h);
        }
        return out;
    }

    private void mergeEsScoredHitsUnlimited(
            Map<Long, RagRetrievalScoredHit> merged,
            long tenantId,
            long kbId,
            String query,
            int candidateK,
            Map<Long, Double> milvusRawByChunk) {
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient == null) {
            return;
        }
        for (ElasticsearchScoredCitationHit es :
                esClient.searchScoredCitationHits(tenantId, kbId, query, candidateK)) {
            if (merged.size() >= candidateK) {
                break;
            }
            RagCitationHit c = es.citation();
            merged.putIfAbsent(
                    c.chunkId(),
                    new RagRetrievalScoredHit(
                            c,
                            RagRetrievalHitSource.ES,
                            milvusRawByChunk.get(c.chunkId()),
                            es.score()));
        }
    }

    private List<RagCitationHit> searchHybridCitationsWithOptionalLtr(
            long tenantId, long kbId, String query, int topK, RagRetrievalProfile profile) {
        if (RagRetrievalProfile.effective(profile) != RagRetrievalProfile.COMPLEX_HYBRID) {
            return searchHybridCitations(tenantId, kbId, query, topK);
        }
        String q = query == null ? "" : query;
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, q);
        int candidateK = ragRetrievalLtrService.candidatePoolSize(tenantId, topK);
        List<RagVectorRecallHit> milvusHits =
                vectorStorePort.searchVectors(tenantId, collectionName(kbId), vec, candidateK);
        double minCos = resolveChatVectorMinCosineScore(tenantId, kbId);
        List<RagRetrievalScoredHit> scored =
                searchHybridScoredCitations(tenantId, kbId, q, topK, milvusHits, minCos);
        return scored.stream().map(RagRetrievalScoredHit::citation).toList();
    }

    private RagRetrievalMode resolveEffectiveMode(long tenantId, RagRetrievalProfile profile) {
        if (RagRetrievalProfile.effective(profile) == RagRetrievalProfile.SIMPLE_VECTOR) {
            return RagRetrievalMode.MILVUS;
        }
        return tenantRagRuntimeResolver.resolveRetrievalMode(tenantId);
    }

    private void mergeEsScoredHits(
            Map<Long, RagRetrievalScoredHit> merged,
            Set<Long> seenDocIds,
            long tenantId,
            long kbId,
            String query,
            int topK,
            Map<Long, Double> milvusRawByChunk) {
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient == null) {
            return;
        }
        for (ElasticsearchScoredCitationHit es :
                esClient.searchScoredCitationHits(tenantId, kbId, query, topK)) {
            if (merged.size() >= topK) {
                break;
            }
            RagCitationHit c = es.citation();
            RagRetrievalScoredHit scored =
                    new RagRetrievalScoredHit(
                            c,
                            RagRetrievalHitSource.ES,
                            milvusRawByChunk.get(c.chunkId()),
                            es.score());
            putHybridScoredHit(merged, seenDocIds, scored, topK);
        }
    }

    private static boolean putHybridScoredHit(
            Map<Long, RagRetrievalScoredHit> merged,
            Set<Long> seenDocIds,
            RagRetrievalScoredHit hit,
            int topK) {
        if (merged.size() >= topK) {
            return false;
        }
        RagCitationHit c = hit.citation();
        if (seenDocIds.contains(c.documentId())) {
            return false;
        }
        if (merged.putIfAbsent(c.chunkId(), hit) != null) {
            return false;
        }
        seenDocIds.add(c.documentId());
        return true;
    }

    private List<RagRetrievalScoredHit> esKeywordScoredHitsOnly(
            long tenantId, long kbId, String query, int topK) {
        Map<Long, RagRetrievalScoredHit> merged = new LinkedHashMap<>();
        Set<Long> seenDocIds = new LinkedHashSet<>();
        mergeEsScoredHits(merged, seenDocIds, tenantId, kbId, query, topK, Map.of());
        return new ArrayList<>(merged.values());
    }

    private List<String> esKeywordSnippetsOnly(long tenantId, long kbId, String query, int topK) {
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient == null) {
            return List.of();
        }
        return esClient.searchContents(tenantId, kbId, query, topK);
    }

    private List<String> mergeHybridSnippetsForTest(
            long tenantId, long kbId, String query, int topK, List<String> milvusSnippets) {
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient == null) {
            return milvusSnippets;
        }
        return mergeTwoListsDedupe(topK, milvusSnippets, esClient.searchContents(tenantId, kbId, query, topK));
    }

    private List<String> milvusHitsToSnippets(
            long tenantId, long kbId, List<RagVectorRecallHit> hits, int topK, double minCos) {
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

    private static String buildRetrievalHint(
            int milvusRecall,
            int afterCosine,
            int resolvable,
            double minCos,
            double maxSim,
            List<RagRetrievalScoredHit> finalHits) {
        if (milvusRecall == 0) {
            return "Milvus 未召回任何分片：请确认文档状态为「已发布」、入库时 Milvus 写入成功，或对本库执行「触发索引」。"
                    + " 混合检索模式下历史文档可能仅有 Milvus 无 ES 词条。";
        }
        if (afterCosine == 0) {
            long esOnly =
                    finalHits == null
                            ? 0
                            : finalHits.stream().filter(h -> h.source() == RagRetrievalHitSource.ES).count();
            String base =
                    "Milvus 召回 "
                            + milvusRecall
                            + " 条，但均未达到本库对话向量阈值 "
                            + minCos
                            + "（最高相似度 "
                            + String.format(java.util.Locale.ROOT, "%.4f", maxSim)
                            + "）。";
            if (esOnly > 0) {
                base += " 已用 ES 关键词兜底 " + esOnly + " 条（BM25 见 keywordScore / 日志 ES#n）。";
            } else {
                base += " 混合检索已尝试 ES 关键词兜底，仍无命中。";
            }
            if (maxSim < 0.2d) {
                base +=
                        " 相似度极低，请检查：① 知识库绑定向量模型与入库时是否一致 ② 是否需对本库「触发索引」③ Milvus 向量维度是否与当前模型一致。";
            } else {
                base += " 可在知识库高级设置中调低 chat_vector_min_cosine_score。";
            }
            return base;
        }
        long esOnly =
                finalHits == null
                        ? 0
                        : finalHits.stream().filter(h -> h.source() == RagRetrievalHitSource.ES).count();
        if (esOnly > 0 && afterCosine > 0) {
            return "Milvus 阈值内 "
                    + afterCosine
                    + " 条，另合并 ES 关键词补充 "
                    + esOnly
                    + " 条（BM25 见各行 keywordScore / 日志 ES#n）。";
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
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient == null) {
            if (mil.isEmpty()) {
                log.debug("[知识库检索] 混合模式未配置 Elasticsearch 客户端，向量无命中则返回空");
            }
            return mil;
        }
        if (mil.isEmpty()) {
            return esClient.searchContents(tenantId, kbId, query, topK);
        }
        return mergeTwoListsDedupe(topK, mil, esClient.searchContents(tenantId, kbId, query, topK));
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
        if (mil.isEmpty()) {
            return mergeHybridCitationHits(
                    List.of(), esClient.searchCitationHits(tenantId, kbId, query, topK), topK);
        }
        return mergeHybridCitationHits(
                mil, esClient.searchCitationHits(tenantId, kbId, query, topK), topK);
    }

    /**
     * 混合检索合并：Milvus 结果在前；按 {@code chunkId} 去重；同一 {@code documentId} 只保留一条（对话 RAG 与试跑一致）。
     */
    private static List<RagCitationHit> mergeHybridCitationHits(
            List<RagCitationHit> milvusFirst, List<RagCitationHit> fromEs, int topK) {
        Map<Long, RagCitationHit> byChunk = new LinkedHashMap<>();
        Set<Long> seenDocIds = new LinkedHashSet<>();
        for (RagCitationHit h : milvusFirst) {
            putHybridCitation(byChunk, seenDocIds, h, topK);
            if (byChunk.size() >= topK) {
                return new ArrayList<>(byChunk.values());
            }
        }
        for (RagCitationHit h : fromEs) {
            putHybridCitation(byChunk, seenDocIds, h, topK);
            if (byChunk.size() >= topK) {
                break;
            }
        }
        return new ArrayList<>(byChunk.values());
    }

    private static void putHybridCitation(
            Map<Long, RagCitationHit> byChunk, Set<Long> seenDocIds, RagCitationHit hit, int topK) {
        if (byChunk.size() >= topK) {
            return;
        }
        if (seenDocIds.contains(hit.documentId())) {
            return;
        }
        if (byChunk.putIfAbsent(hit.chunkId(), hit) == null) {
            seenDocIds.add(hit.documentId());
        }
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
