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
                "ragQueryPort.searchSnippets tenantId={} kbId={} mode={} topK={} queryChars={} resultCount={}",
                tid,
                kb,
                mode,
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
                "ragQueryPort.searchCitationHits tenantId={} kbId={} retrievalMode={} topK={} queryChars={} resultCount={}",
                tid,
                kb,
                mode,
                topK,
                query == null ? 0 : query.length(),
                out.size());
        return out;
    }

    @Override
    public List<String> searchSnippetsAcrossKnowledgeBases(Long tenantId, List<Long> kbIds, String query, int topK) {
        long tid = tenantId == null ? 0L : tenantId;
        if (kbIds == null || kbIds.isEmpty()) {
            log.info("ragQueryPort.searchSnippetsAcrossKnowledgeBases skipped emptyKbList tenantId={}", tid);
            return List.of();
        }
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        RagRetrievalMode mode = aiRagProperties.resolvedRetrievalMode();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Long kb : kbIds) {
            if (kb == null) {
                continue;
            }
            List<String> part =
                    switch (mode) {
                        case MILVUS -> searchMilvusSnippets(tid, kb, query, topK);
                        case MILVUS_ES_HYBRID -> searchHybridSnippets(tid, kb, query, topK);
                    };
            addUpTo(set, part, topK);
            if (set.size() >= topK) {
                break;
            }
        }
        List<String> out = truncateList(new ArrayList<>(set), topK);
        log.info(
                "ragQueryPort.searchSnippetsAcrossKnowledgeBases tenantId={} kbCount={} mode={} topK={} queryChars={} resultCount={}",
                tid,
                kbIds.size(),
                mode,
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
            log.info("ragQueryPort.searchCitationHitsAcrossKnowledgeBases skipped emptyKbList tenantId={}", tid);
            return List.of();
        }
        if (aiProvidersProperties.resolvedVectorStore() != VectorStoreProviderMode.milvus) {
            return List.of();
        }
        RagRetrievalMode mode = aiRagProperties.resolvedRetrievalMode();
        Map<Long, RagCitationHit> byChunk = new LinkedHashMap<>();
        for (Long kb : kbIds) {
            if (kb == null) {
                continue;
            }
            List<RagCitationHit> part =
                    switch (mode) {
                        case MILVUS -> searchMilvusCitations(tid, kb, query, topK);
                        case MILVUS_ES_HYBRID -> searchHybridCitations(tid, kb, query, topK);
                    };
            for (RagCitationHit h : part) {
                byChunk.putIfAbsent(h.chunkId(), h);
                if (byChunk.size() >= topK) {
                    return new ArrayList<>(byChunk.values());
                }
            }
        }
        List<RagCitationHit> out = new ArrayList<>(byChunk.values());
        log.info(
                "ragQueryPort.searchCitationHitsAcrossKnowledgeBases tenantId={} kbCount={} retrievalMode={} topK={} queryChars={} resultCount={}",
                tid,
                kbIds.size(),
                mode,
                topK,
                query == null ? 0 : query.length(),
                out.size());
        return out;
    }

    private List<String> searchMilvusSnippets(long tenantId, long kbId, String query, int topK) {
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, query == null ? "" : query);
        List<RagVectorRecallHit> hits = vectorStorePort.searchVectors(tenantId, collectionName(kbId), vec, topK);
        return hitsToSnippets(tenantId, kbId, hits, topK);
    }

    private List<RagCitationHit> searchMilvusCitations(long tenantId, long kbId, String query, int topK) {
        float[] vec = ragEmbeddingPort.embed(tenantId, kbId, query == null ? "" : query);
        List<RagVectorRecallHit> hits = vectorStorePort.searchVectors(tenantId, collectionName(kbId), vec, topK);
        return hitsToCitations(tenantId, kbId, hits, topK);
    }

    private List<String> searchHybridSnippets(long tenantId, long kbId, String query, int topK) {
        List<String> mil = searchMilvusSnippets(tenantId, kbId, query, topK);
        List<String> es = List.of();
        ElasticsearchRagSearchClient esClient = elasticsearchRagSearchClient.getIfAvailable();
        if (esClient != null) {
            es = esClient.searchContents(tenantId, kbId, query, topK);
        } else {
            log.debug("MILVUS_ES_HYBRID锛氭湭瑁呴厤 ElasticsearchRagSearchClient锛屼粎浣跨敤 Milvus 缁撴灉");
        }
        return mergeTwoListsDedupe(topK, mil, es);
    }

    private List<RagCitationHit> searchHybridCitations(long tenantId, long kbId, String query, int topK) {
        List<RagCitationHit> mil = searchMilvusCitations(tenantId, kbId, query, topK);
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
                    .findCitationHitForKb(tenantId, kbId, chunkId)
                    .map(RagCitationHit::contentPreview)
                    .filter(p -> p != null && !p.isBlank())
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
     * <p>多库检索（{@code *AcrossKnowledgeBases}）时对每个 {@code kbId} 分别检索并在本方法按库解析，互不混用。
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
