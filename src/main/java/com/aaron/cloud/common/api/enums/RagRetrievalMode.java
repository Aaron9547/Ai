package com.aaron.cloud.common.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RAG 检索实现；与 {@code ai.rag.retrieval-mode} 对齐。仅保留向量路径：纯 Milvus 或与 Elasticsearch 关键词混合（无 MySQL 词法召回）。
 */
@Getter
@RequiredArgsConstructor
public enum RagRetrievalMode {
    /** 仅 Milvus 近似检索 + MySQL 仅用于分片正文/元数据拼装。 */
    MILVUS("milvus"),
    /** Milvus 向量 + ES 关键词；ES 不可用时退化为仅 Milvus。 */
    MILVUS_ES_HYBRID("milvus_es_hybrid");

    private final String storageValue;

    public static RagRetrievalMode fromYaml(String raw) {
        if (raw == null || raw.isBlank()) {
            return MILVUS;
        }
        String s = raw.trim().toLowerCase();
        for (RagRetrievalMode m : values()) {
            if (m.storageValue.equals(s) || m.name().equalsIgnoreCase(s)) {
                return m;
            }
        }
        // 兼容历史配置键
        if ("mysql_lexical".equals(s)) {
            return MILVUS;
        }
        return MILVUS;
    }
}
