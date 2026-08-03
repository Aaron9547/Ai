package com.aaron.cloud.common.api.dto;

/**
 * Milvus 等向量库单条召回；{@code embeddingRef} 与 {@code rag_chunk.embedding_ref} / 入库主键一致（通常为 chunk 主键字符串）。
 */
public record RagVectorRecallHit(String embeddingRef, float score) {}
