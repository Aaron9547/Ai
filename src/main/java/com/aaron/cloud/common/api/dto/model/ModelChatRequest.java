package com.aaron.cloud.common.api.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelChatRequest {
    private Long tenantId;
    private String deviceId;
    private Long userId;
    private String modelAlias;
    /** 为 true 且厂商 SSE 含 reasoning 增量时，通过 {@link #reasoningTokenConsumer} 输出 */
    private Boolean thinkingEnabled;

    private List<MessageTurn> messages;

    /** OpenAI 兼容 tools；非空时上游可返回 tool_calls。 */
    private List<ModelToolDefinition> tools;

    private String toolChoice;

    /** 对话场景传会话 id，写入计量 {@code ref_json.conversationId}；非对话编排可省略或传 {@code 0}。 */
    private Long conversationId;

    /** 为 true 时跳过流式结束后的自动 token 计量（极少数编排自行落库时使用）。 */
    private Boolean skipUsageRecord;

    /**
     * {@link com.aaron.cloud.common.api.enums.metering.LlmUsageScene#getCode()}；写入计量 {@code ref_json.usageScene}。
     */
    private String usageScene;

    @JsonIgnore private transient Consumer<String> reasoningTokenConsumer;

    /** 编排层置位后，上游 SSE 读取循环应尽快结束（客户端断开或思考护栏触发）。 */
    @JsonIgnore private transient AtomicBoolean streamCancelled;

    /** 流式最后一帧解析到 usage 时回调（由编排层落库计量、累加模型共用额度等） */
    @JsonIgnore private transient Consumer<ModelTokenUsage> streamUsageConsumer;

    /**
     * 编排层写入：本次流式实际计费用 {@code llm_model.id}（主备切换后与请求 {@link #modelAlias} 可能不一致）。
     */
    @JsonIgnore private transient java.util.concurrent.atomic.AtomicReference<Long> resolvedLlmModelIdRef;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageTurn {
        private String role;
        private String content;
        /** assistant 角色：模型返回的 tool_calls */
        private List<ModelToolCall> toolCalls;
        /** tool 角色 */
        private String toolCallId;
        private String name;
    }
}
