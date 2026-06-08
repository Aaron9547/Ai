package com.aaron.cloud.chat.rag;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile;

/** 对话 RAG 检索问句规划结果（改写 + 语义门控 + 简单/复杂分流）。 */
public record RagRetrievalQueryPlan(
        String originalQuery,
        String retrievalQuery,
        boolean rewriteApplied,
        boolean rewriteRejected,
        Double rewriteSimilarity,
        RagRetrievalProfile profile) {}
