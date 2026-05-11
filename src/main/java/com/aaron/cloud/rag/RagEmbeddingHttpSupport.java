package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.LlmVectorBackend;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 嵌入 HTTP：按 {@link LlmVectorBackend} 组装请求 JSON、解析成功响应中的向量数组（与 {@link RagEmbeddingService} 配套）。
 */
final class RagEmbeddingHttpSupport {

    static final int MAX_INPUT_CHARS = 8000;

    private RagEmbeddingHttpSupport() {}

    static String buildEmbeddingsRequestBody(
            ObjectMapper objectMapper, String modelId, String text, LlmVectorBackend vectorBackend)
            throws Exception {
        String payload = text == null ? "" : text;
        if (payload.length() > MAX_INPUT_CHARS) {
            payload = payload.substring(0, MAX_INPUT_CHARS);
        }
        if (vectorBackend == LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", modelId);
            ArrayNode input = root.putArray("input");
            ObjectNode block = input.addObject();
            block.put("type", "text");
            block.put("text", payload);
            return objectMapper.writeValueAsString(root);
        }
        return objectMapper
                .createObjectNode()
                .put("model", modelId)
                .put("input", payload)
                .toString();
    }

    /**
     * OpenAI 列表形态：{@code data[0].embedding}；方舟多模态：{@code data.embedding}（{@code data} 为对象）。若主路径无向量则回退尝试另一形态。
     */
    static List<Float> parseEmbeddingsResponse(JsonNode root, LlmVectorBackend vectorBackend) {
        if (vectorBackend == LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL) {
            List<Float> fromObj = readMultimodalObjectEmbedding(root);
            if (!fromObj.isEmpty()) {
                return fromObj;
            }
            List<Float> fallback = readOpenAiListEmbedding(root);
            if (!fallback.isEmpty()) {
                return fallback;
            }
        } else {
            List<Float> openAi = readOpenAiListEmbedding(root);
            if (!openAi.isEmpty()) {
                return openAi;
            }
            List<Float> mm = readMultimodalObjectEmbedding(root);
            if (!mm.isEmpty()) {
                return mm;
            }
        }
        throw new IllegalStateException("embeddings 响应缺少 embedding 数组");
    }

    private static List<Float> readOpenAiListEmbedding(JsonNode root) {
        JsonNode emb = root.path("data").path(0).path("embedding");
        return readFloatArray(emb);
    }

    /** 方舟多模态成功体：{@code "data": { "embedding": [ ... ] } } */
    private static List<Float> readMultimodalObjectEmbedding(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isObject()) {
            return List.of();
        }
        return readFloatArray(data.path("embedding"));
    }

    private static List<Float> readFloatArray(JsonNode emb) {
        if (!emb.isArray() || emb.isEmpty()) {
            return List.of();
        }
        List<Float> floats = new ArrayList<>();
        for (JsonNode n : emb) {
            if (n.isNumber()) {
                floats.add(n.floatValue());
            }
        }
        return floats;
    }
}
