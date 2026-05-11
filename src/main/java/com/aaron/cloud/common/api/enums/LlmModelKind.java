package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户可配模型类型；同一 Base URL + API Key 下可配置多条记录，仅 {@link #openaiModelId}
 * 等厂商字段不同。
 *
 * <p>对话流式补全（C 端选择器、OpenAI Chat Completions）仅允许 {@link #LANGUAGE}；其余类型供内部编排（向量、多模态路由等）解析。
 *
 * <p>Token 用量与 {@code llm_model} 共用额度对<strong>全部类型</strong>生效；内部编排取得厂商 usage 后须与对话路径一致地累加
 * {@code tokens_used} 并写计量流水，不得仅对语言模型计数。
 */
@Getter
@RequiredArgsConstructor
public enum LlmModelKind {
    /** 文本对话 / Chat Completions（用户端唯一可选）。 */
    LANGUAGE("LANGUAGE"),
    /** 语音合成/识别等（内部使用）。 */
    SPEECH("SPEECH"),
    /** 视觉理解等（内部使用）。 */
    VISION("VISION"),
    /**
     * 向量 / 文本嵌入（内部使用）：管理端配置为租户 {@code llm_model} 后，由知识库 {@code assigned_embedding_model_id} 绑定；
     * 运行时走 OpenAI 兼容嵌入请求体（{@code model} + {@code input}）；URL 由 {@code llm_model.vector_backend} 决定。产出维数须与
     * {@code com.aaron.cloud.providers.milvus.vector-dimension} 一致。
     */
    VECTOR("VECTOR"),
    /** 智能路由 / 融合网关侧模型（内部使用）。 */
    SMART_ROUTING("SMART_ROUTING");

    @EnumValue private final String code;

    public static LlmModelKind fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return LANGUAGE;
        }
        String s = raw.trim();
        for (LlmModelKind k : values()) {
            if (k.code.equalsIgnoreCase(s) || k.name().equalsIgnoreCase(s)) {
                return k;
            }
        }
        return LANGUAGE;
    }
}
