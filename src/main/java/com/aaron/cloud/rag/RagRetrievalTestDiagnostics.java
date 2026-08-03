package com.aaron.cloud.rag;

/** 管理端检索试跑：Milvus 召回与阈值过滤后的条数，便于解释「已发布但试跑为空」。 */
public record RagRetrievalTestDiagnostics(
        int milvusRecallCount,
        int afterCosineThresholdCount,
        int resolvableChunkCount,
        double minCosineThreshold,
        double maxMilvusSimilarity,
        String hint) {}
