package com.aaron.cloud.common.modelcfg.quota;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM 单次调用 usage 异步落库消息（RocketMQ 或 Redis List 载荷 JSON）；适用于任意 {@code llm_model.model_kind}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmUsageDigestMessage(
        long tenantId,
        Long userId,
        String deviceId,
        long llmModelId,
        String modelAlias,
        long conversationId,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        /** {@link com.aaron.cloud.common.api.enums.MeteringMeterType#getCode()}；缺省按语言对话计量 */
        String meterTypeCode,
        /** {@link com.aaron.cloud.common.api.enums.LlmModelKind#getCode()}；可空 */
        String modelKindCode,
        /**
         * 本次模型流式调用 wall-clock 耗时（毫秒）；并入同一条 token 计量事件的 {@code ref_json}，避免再单独写
         * {@code MODEL_COMPLETION} 行。旧队列 JSON 缺本字段时为 null。
         */
        Long durationMs,
        /** {@link com.aaron.cloud.common.api.enums.metering.LlmUsageScene#getCode()}；可空 */
        String usageSceneCode) {

    /** 兼容旧队列 JSON（无后两字段时 Jackson 反序列化为 null）。 */
    public LlmUsageDigestMessage {
        meterTypeCode = meterTypeCode == null ? "" : meterTypeCode;
        modelKindCode = modelKindCode == null ? "" : modelKindCode;
        usageSceneCode = usageSceneCode == null ? "" : usageSceneCode;
    }
}
