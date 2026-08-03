package com.aaron.cloud.chat;

import com.aaron.cloud.common.api.enums.intent.IntentRoute;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.aaron.cloud.common.api.enums.infra.VectorStoreProviderMode;

/** 对话 / RAG 流水线日志的中文可读标签（仅用于 log 输出）。 */
public final class PipelineLogZh {

    private PipelineLogZh() {}

    public static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    public static String mockCall(boolean mock) {
        return mock ? "模拟" : "真实";
    }

    public static String intentRoute(IntentRoute route) {
        if (route == null) {
            return "未知";
        }
        return switch (route) {
            case CHAT_ONLY -> "纯对话（未注入知识库）";
            case RAG -> "知识库增强";
            case MCP_TOOLS -> "MCP 工具";
        };
    }

    public static String retrievalMode(RagRetrievalMode mode) {
        if (mode == null) {
            return "未知";
        }
        return switch (mode) {
            case MILVUS -> "Milvus 向量检索";
            case MILVUS_ES_HYBRID -> "Milvus + Elasticsearch 混合检索";
        };
    }

    public static String vectorStore(VectorStoreProviderMode mode) {
        if (mode == null) {
            return "未知";
        }
        return switch (mode) {
            case milvus -> "Milvus";
            case local -> "本地占位（未接向量库）";
        };
    }
}
