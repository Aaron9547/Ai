package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.LlmVectorBackend;

/**
 * 向量嵌入请求 URL：按 {@link LlmVectorBackend} 区分路径拼接，供 {@link RagEmbeddingService} 使用。
 */
public final class VectorEmbeddingsUrl {

    private VectorEmbeddingsUrl() {}

    public static String resolve(String openaiBaseUrl, LlmVectorBackend backend) {
        LlmVectorBackend b = backend != null ? backend : LlmVectorBackend.OPENAI_COMPATIBLE;
        return switch (b) {
            case VOLCENGINE_ARK -> suffixEmbeddingsOnly(openaiBaseUrl);
            case VOLCENGINE_ARK_MULTIMODAL -> volcArkMultimodalEmbeddings(openaiBaseUrl);
            case OPENAI_COMPATIBLE -> openAiV1UnderServiceRoot(openaiBaseUrl);
        };
    }

    /** 兼容根 + {@code /embeddings}（不插入 {@code /v1}）。 */
    private static String suffixEmbeddingsOnly(String openaiBaseUrl) {
        String s = normalizeBase(openaiBaseUrl);
        if (s.isEmpty()) {
            return "";
        }
        if (s.endsWith("/embeddings")) {
            return s;
        }
        return s + "/embeddings";
    }

    /**
     * 服务根下 OpenAI 兼容 v1 嵌入路径：{@code …/v1/embeddings}；若 Base 已以 {@code /v1} 结尾则仅再补 {@code /embeddings}。
     */
    private static String openAiV1UnderServiceRoot(String openaiBaseUrl) {
        String s = normalizeBase(openaiBaseUrl);
        if (s.isEmpty()) {
            return "";
        }
        if (s.endsWith("/embeddings")) {
            return s;
        }
        if (s.endsWith("/v1")) {
            return s + "/embeddings";
        }
        return s + "/v1/embeddings";
    }

    /**
     * 方舟多模态向量化：{@code …/api/v3/embeddings/multimodal}；若 Base 已以 {@code /embeddings} 结尾则仅补 {@code /multimodal}。
     */
    private static String volcArkMultimodalEmbeddings(String openaiBaseUrl) {
        String s = normalizeBase(openaiBaseUrl);
        if (s.isEmpty()) {
            return "";
        }
        if (s.endsWith("/embeddings/multimodal")) {
            return s;
        }
        if (s.endsWith("/embeddings")) {
            return s + "/multimodal";
        }
        return s + "/embeddings/multimodal";
    }

    private static String normalizeBase(String openaiBaseUrl) {
        String s = openaiBaseUrl == null ? "" : openaiBaseUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
