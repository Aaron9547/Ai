package com.aaron.cloud.common.api.ports;

/**
 * 将用户问句或分片正文编码为与 Milvus collection 维数一致的向量（标准 RAG 的 embedding 步骤）。
 *
 * <p>解析规则：按 {@code kbId} 加载知识库绑定的 {@code llm_model}（须 {@code VECTOR}），按 {@code llm_model.vector_backend} 拼接嵌入
 * URL 并 {@code POST} OpenAI 兼容 {@code model}/{@code input}；可无 API Key（不发送 {@code Authorization}）。
 */
public interface RagEmbeddingPort {

    /**
     * @param tenantId 租户隔离
     * @param kbId 知识库主键（用于解析绑定的向量模型）
     */
    float[] embed(long tenantId, long kbId, String text);

    /** 与 {@code ai.providers.milvus.vector-dimension} 及 Milvus collection 一致（各 KB 所用嵌入模型产出维数须与此对齐）。 */
    int dimensions();
}
