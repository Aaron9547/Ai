package com.aaron.cloud.common.api.dto;

/**
 * 词法 RAG 召回的一条可引用分片（用于写入助手消息 meta 与命中计数；{@code chunkSeq} 与
 * {@code lnk_rag_document_chunk.seq} 一致，从 0 起；展示「第 n 片」时用 {@code chunkSeq + 1}）。
 */
public record RagCitationHit(
        long kbId,
        long documentId,
        String documentTitle,
        long chunkId,
        int chunkSeq,
        String contentPreview) {}
