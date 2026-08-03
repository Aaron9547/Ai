package com.aaron.cloud.common.observability;

import com.aaron.cloud.common.api.enums.observability.McpTraceSourceScene;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import lombok.Builder;
import lombok.Value;

/** 对话/MCP/RAG 链路 ThreadLocal 上下文（热路径仅 set/get，finally clear）。 */
public final class ObservabilityTraceContext {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private ObservabilityTraceContext() {}

    public static void open(Snapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static Snapshot current() {
        return HOLDER.get();
    }

    public static void setLlmRound(int llmRound) {
        Snapshot s = HOLDER.get();
        if (s != null) {
            HOLDER.set(s.toBuilder().llmRound(llmRound).build());
        }
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Value
    @Builder(toBuilder = true)
    public static class Snapshot {
        String orchestrationTraceId;
        String httpTraceId;
        Long tenantId;
        Long userId;
        Long conversationId;
        String conversationPublicId;
        Long userMessageId;
        McpTraceSourceScene sourceScene;
        int llmRound;
        RagRetrievalMode retrievalMode;
        String ragQueryText;
    }
}
