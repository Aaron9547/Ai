package com.aaron.cloud.common.api.enums.metering;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** metering_usage_event.meter_type 取值（与 DDL 注释一致） */
@Getter
@RequiredArgsConstructor
public enum MeteringMeterType {
    /** 语言模型对话流（OpenAI Chat Completions 等）。 */
    LLM_CHAT_COMPLETION("llm.chat.completion"),
    /** 非对话类 LLM 调用（向量、语音、视觉、路由等）共用 token 计量码值。 */
    LLM_MODEL_USAGE("llm.model.usage");

    @EnumValue private final String code;

    /** 落库/展示用；未知或非空但无匹配时归为 {@link #LLM_MODEL_USAGE}。 */
    public static MeteringMeterType resolveForPersist(String code) {
        if (code == null || code.isBlank()) {
            return LLM_CHAT_COMPLETION;
        }
        for (MeteringMeterType v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        return LLM_MODEL_USAGE;
    }
}
