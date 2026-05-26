package com.aaron.cloud.rag;

import java.util.List;

/** 管理端检索试跑一次 Milvus 向量检索的聚合结果（避免重复 embed/search）。 */
public record RagRetrievalTestSearchResult(
        List<RagRetrievalScoredHit> hits,
        List<String> snippets,
        RagRetrievalTestDiagnostics diagnostics) {}
