package com.aaron.cloud.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析对端 {@code WebResult<List<List<Double>>>} 风格 JSON：取 {@code data[0]} 为第一条文本的向量。
 */
final class RagLocalEmbeddingFeignSupport {

    private RagLocalEmbeddingFeignSupport() {}

    static List<Float> parseFirstEmbeddingVector(String body, ObjectMapper objectMapper) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("本地嵌入 Feign 响应缺少 data 向量数组");
        }
        JsonNode first = data.get(0);
        if (first == null || !first.isArray() || first.isEmpty()) {
            throw new IllegalStateException("本地嵌入 Feign 响应 data[0] 非向量数组");
        }
        List<Float> floats = new ArrayList<>();
        for (JsonNode n : first) {
            if (n.isNumber()) {
                floats.add(n.floatValue());
            }
        }
        if (floats.isEmpty()) {
            throw new IllegalStateException("本地嵌入 Feign 响应向量为空");
        }
        return floats;
    }
}
