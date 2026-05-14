package com.aaron.cloud.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户分层记忆：进程级开关与队列（Milvus collection、Redis 队列名等）；策略预算见租户运行参数 {@code MEMORY_POLICY_JSON}。
 */
@Data
@ConfigurationProperties(prefix = "ai.memory")
public class AiMemoryProperties {

    /**
     * 为 true 且 {@code ai.providers.vector-store=milvus} 时，对记忆 chunk 写入专用 Milvus collection 并支持向量召回。
     */
    private boolean vectorEnabled = false;

    /** Milvus collection 名（与 RAG 的 kb_* 隔离）。 */
    private String milvusCollection = "user_memory_chunk";

    private String abstractRedisQueueKey = "ai:queue:memory-abstract";

    /** Redis 队列轮询间隔（毫秒），仅 RocketMQ 未启用时生效。 */
    private long abstractRedisPollMs = 400;
}
