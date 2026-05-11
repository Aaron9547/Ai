package com.aaron.cloud.rag;

import com.aaron.cloud.common.config.properties.AiProvidersProperties;
import com.aaron.cloud.common.config.providers.VectorStoreProviderMode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 向向量库是否以真实 Milvus 接入：{@code ai.providers.vector-store=local} 时为占位，对话侧静默跳过 RAG，管理端写入须显式拒绝。
 */
@Component
@RequiredArgsConstructor
public class RagVectorInfrastructure {

    private final AiProvidersProperties aiProvidersProperties;

    public boolean isMilvusVectorStore() {
        return aiProvidersProperties.resolvedVectorStore() == VectorStoreProviderMode.milvus;
    }

    /**
     * 需要写入向量、Embedding 或开启对话检索开关时调用；HTTP 503 便于管理端展示明确原因（见 {@code .cursorrules} 用户向文案）。
     */
    public void assertMilvusOrThrow() {
        if (!isMilvusVectorStore()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "未启用 Milvus 向量库：知识库上传、索引入库与对话知识检索已暂停。请配置 ai.providers.vector-store=milvus 并连通 Milvus 后重试；浏览列表与文档正文仍可查看。");
        }
    }
}
