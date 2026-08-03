package com.aaron.cloud.common.api.enums.llm;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 向量模型（{@link LlmModelKind#VECTOR}）嵌入 HTTP 路径与请求体策略；VECTOR 行在 {@code llm_model.integration_backend} 存本枚举 {@link #code}。非向量类型行可存默认值，不参与编排。
 *
 * <p>{@link #OPENAI_COMPATIBLE}：对服务根地址补 {@code /v1/embeddings}（若 Base 已以 {@code /v1} 结尾则只补 {@code /embeddings}），与内网统一嵌入网关常见 POST URI 一致；请求体为 OpenAI 兼容 {@code model}+{@code input} 字符串。
 *
 * <p>{@link #VOLCENGINE_ARK}：兼容根仅追加 {@code /embeddings}（如方舟文本向量化 {@code …/api/v3/embeddings}）；请求体同 OpenAI 兼容。
 *
 * <p>{@link #VOLCENGINE_ARK_MULTIMODAL}：火山方舟「多模态向量化」{@code POST …/api/v3/embeddings/multimodal}（见官方文档）；请求体为 {@code model}+{@code input} 内容块数组（RAG 纯文本仅送 {@code type:text}）；成功响应为 {@code data.embedding} 单数组。适用于 {@code doubao-embedding-vision-*} 等<strong>非</strong> {@code /embeddings} 文本接口的模型。
 *
 * <p>{@link #DASHSCOPE_COMPATIBLE}：阿里云百炼 / DashScope OpenAI 兼容嵌入，{@code …/compatible-mode/v1/embeddings}，请求体 {@code model}+{@code input} 字符串；模型如 {@code text-embedding-v4}。
 *
 * <p>{@link #DASHSCOPE_TEXT_EMBEDDING}：百炼 DashScope 原生文本向量 {@code POST …/api/v1/services/embeddings/text-embedding/text-embedding}；请求体 {@code input.texts} 数组；响应 {@code output.embeddings[0].embedding}。
 *
 * <p>{@link #DASHSCOPE_MULTIMODAL_EMBEDDING}：百炼多模态向量 {@code POST …/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding}；RAG 纯文本仅送 {@code input.contents:[{text}]}；响应 {@code output.embeddings[]}（优先 {@code type=text}）。模型如 {@code qwen3-vl-embedding}、{@code tongyi-embedding-vision-plus}、{@code multimodal-embedding-v1}。
 */
@Getter
@RequiredArgsConstructor
public enum LlmVectorBackend {
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE"),
    VOLCENGINE_ARK("VOLCENGINE_ARK"),
    VOLCENGINE_ARK_MULTIMODAL("VOLCENGINE_ARK_MULTIMODAL"),
    DASHSCOPE_COMPATIBLE("DASHSCOPE_COMPATIBLE"),
    DASHSCOPE_TEXT_EMBEDDING("DASHSCOPE_TEXT_EMBEDDING"),
    DASHSCOPE_MULTIMODAL_EMBEDDING("DASHSCOPE_MULTIMODAL_EMBEDDING");

    @EnumValue private final String code;

    public static LlmVectorBackend fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPENAI_COMPATIBLE;
        }
        String s = raw.trim();
        for (LlmVectorBackend b : values()) {
            if (b.code.equalsIgnoreCase(s) || b.name().equalsIgnoreCase(s)) {
                return b;
            }
        }
        return OPENAI_COMPATIBLE;
    }
}
