package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.dto.RagCitationHit;

/** Elasticsearch 关键词分支单条命中（含 BM25 分）。 */
public record ElasticsearchScoredCitationHit(RagCitationHit citation, double score) {}
