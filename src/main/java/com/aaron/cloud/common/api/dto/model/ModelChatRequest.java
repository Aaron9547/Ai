package com.aaron.cloud.common.api.dto.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
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

    @JsonIgnore private transient Consumer<String> reasoningTokenConsumer;

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
    }
}
