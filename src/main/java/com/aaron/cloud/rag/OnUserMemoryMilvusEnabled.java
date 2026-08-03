package com.aaron.cloud.rag;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/** Milvus 向量库已启用且 {@code ai.memory.vector-enabled=true} 时装配 {@link UserMemoryMilvusStore}。 */
public class OnUserMemoryMilvusEnabled extends AllNestedConditions {

    public OnUserMemoryMilvusEnabled() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(name = "ai.providers.vector-store", havingValue = "milvus")
    static class VectorStoreIsMilvus {}

    @ConditionalOnProperty(name = "ai.memory.vector-enabled", havingValue = "true")
    static class MemoryVectorFlagOn {}
}
