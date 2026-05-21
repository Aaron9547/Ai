package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aaron.cloud.common.api.enums.LlmVectorBackend;
import org.junit.jupiter.api.Test;

class VectorEmbeddingsUrlTest {

    @Test
    void openAiCompatible_serviceRoot_appendsV1Embeddings() {
        assertEquals(
                "https://aiservice.example.com/ly-ai/rag/v1/embeddings",
                VectorEmbeddingsUrl.resolve("https://aiservice.example.com/ly-ai/rag", LlmVectorBackend.OPENAI_COMPATIBLE));
        assertEquals(
                "https://aiservice.example.com/ly-ai/rag/v1/embeddings",
                VectorEmbeddingsUrl.resolve("https://aiservice.example.com/ly-ai/rag/", LlmVectorBackend.OPENAI_COMPATIBLE));
    }

    @Test
    void openAiCompatible_baseAlreadyV1_appendsEmbeddingsOnly() {
        assertEquals(
                "https://api.openai.com/v1/embeddings",
                VectorEmbeddingsUrl.resolve("https://api.openai.com/v1", LlmVectorBackend.OPENAI_COMPATIBLE));
    }

    @Test
    void volcengineArk_apiV3_appendsEmbeddingsOnly() {
        assertEquals(
                "https://ark.cn-beijing.volces.com/api/v3/embeddings",
                VectorEmbeddingsUrl.resolve("https://ark.cn-beijing.volces.com/api/v3", LlmVectorBackend.VOLCENGINE_ARK));
    }

    @Test
    void volcengineArkMultimodal_apiV3_appendsEmbeddingsMultimodal() {
        assertEquals(
                "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal",
                VectorEmbeddingsUrl.resolve(
                        "https://ark.cn-beijing.volces.com/api/v3", LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL));
        assertEquals(
                "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal",
                VectorEmbeddingsUrl.resolve(
                        "https://ark.cn-beijing.volces.com/api/v3/", LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL));
    }

    @Test
    void volcengineArkMultimodal_baseAlreadyEmbeddings_appendsMultimodalOnly() {
        assertEquals(
                "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal",
                VectorEmbeddingsUrl.resolve(
                        "https://ark.cn-beijing.volces.com/api/v3/embeddings", LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL));
    }

    @Test
    void dashscopeCompatible_compatibleModeV1_appendsEmbeddings() {
        assertEquals(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
                VectorEmbeddingsUrl.resolve(
                        "https://dashscope.aliyuncs.com/compatible-mode/v1", LlmVectorBackend.DASHSCOPE_COMPATIBLE));
    }

    @Test
    void dashscopeTextEmbedding_apiV1_appendsServicePath() {
        assertEquals(
                "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding",
                VectorEmbeddingsUrl.resolve(
                        "https://dashscope.aliyuncs.com/api/v1", LlmVectorBackend.DASHSCOPE_TEXT_EMBEDDING));
    }

    @Test
    void dashscopeMultimodalEmbedding_apiV1_appendsServicePath() {
        assertEquals(
                "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding",
                VectorEmbeddingsUrl.resolve(
                        "https://dashscope.aliyuncs.com/api/v1", LlmVectorBackend.DASHSCOPE_MULTIMODAL_EMBEDDING));
    }

    @Test
    void fullEmbeddingsUrl_unchanged() {
        assertEquals(
                "http://127.0.0.1:9999/v1/embeddings",
                VectorEmbeddingsUrl.resolve("http://127.0.0.1:9999/v1/embeddings", LlmVectorBackend.OPENAI_COMPATIBLE));
    }
}
