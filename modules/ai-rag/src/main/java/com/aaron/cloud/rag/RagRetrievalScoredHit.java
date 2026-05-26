package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalHitSource;

/**
 * 检索试跑：可引用分片 + 来源。
 * <ul>
 *   <li>{@link #vectorSimilarity()}：与 Milvus COSINE 一致（同一次 topK 召回的原始分；未进 topK 的 ES 独有为 null）
 *   <li>{@link #keywordScore()}：仅 ES 分支的 BM25，与向量分不可直接比较，仅作关键词相关参考
 * </ul>
 */
public record RagRetrievalScoredHit(
        RagCitationHit citation,
        RagRetrievalHitSource source,
        Double vectorSimilarity,
        Double keywordScore) {}
