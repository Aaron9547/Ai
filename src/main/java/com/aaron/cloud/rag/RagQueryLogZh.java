package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagRetrievalMode;

/** RAG 检索日志的中文可读标签。 */
public final class RagQueryLogZh {

    private RagQueryLogZh() {}

    public static String mode(RagRetrievalMode mode) {
        if (mode == null) {
            return "未知";
        }
        return switch (mode) {
            case MILVUS -> "Milvus 向量检索";
            case MILVUS_ES_HYBRID -> "Milvus + Elasticsearch 混合检索";
        };
    }
}
