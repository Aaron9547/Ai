package com.aaron.cloud.chat.dto;

/**
 * 管理端开放接口展示的 RAG 引用项（与助手消息 {@code meta_json#ragCitations} 数组元素对齐）。
 */
public record RagCitationView(
        long kbId,
        long documentId,
        String documentTitle,
        long chunkId,
        int chunkSeq,
        String contentPreview) {}
