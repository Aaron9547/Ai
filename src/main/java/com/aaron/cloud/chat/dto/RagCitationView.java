package com.aaron.cloud.chat.dto;

/**
 * 绠＄悊绔?寮€鏀炬帴鍙ｅ睍绀虹殑 RAG 寮曠敤椤癸紙涓庡姪鎵嬫秷鎭?{@code meta_json#ragCitations} 鏁扮粍鍏冪礌瀵归綈锛夈€? */
public record RagCitationView(
        long kbId,
        long documentId,
        String documentTitle,
        long chunkId,
        int chunkSeq,
        String contentPreview) {}
