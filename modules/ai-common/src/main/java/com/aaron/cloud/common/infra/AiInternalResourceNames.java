package com.aaron.cloud.common.infra;

/**
 * 进程内固定的 Redis 队列名、MQ Topic/Group、Milvus/ES 资源名等；<strong>不</strong>进入 {@code application.yml}，
 * 运维一般无需修改。若确需按环境区分，应通过部署隔离（多集群/多命名空间）而非 yml 堆叠。
 */
public final class AiInternalResourceNames {

    private AiInternalResourceNames() {}

    /** Redis List 异步队列（RocketMQ 未启用时的兜底通道）。 */
    public static final class RedisQueues {
        private RedisQueues() {}

        public static final String LLM_USAGE = "ai:queue:llm-usage";
        public static final String MEMORY_ABSTRACT = "ai:queue:memory-abstract";
    }

    /** Redis 键前缀（后接业务 id / 租户键等）。 */
    public static final class RedisKeyPrefixes {
        private RedisKeyPrefixes() {}

        public static final String TENANT_RUNTIME_CFG = "ai:cfg:tenant:";
        public static final String PROMPT_TEMPLATE = "ai:prompt:";
        public static final String INTENT_FLOW = "ai:intentFlow:";
    }

    /** RocketMQ Topic 与 Consumer Group（与 {@code ai.rocketmq.enabled} 基础设施开关配合）。 */
    public static final class RocketMq {
        private RocketMq() {}

        public static final String JOB_TOPIC = "ai-job-dispatch";
        public static final String JOB_CONSUMER_GROUP = "ai-job-consumer";

        public static final String LLM_USAGE_TOPIC = "ai-llm-usage";
        public static final String LLM_USAGE_CONSUMER_GROUP = "ai-llm-usage-consumer";

        public static final String MEMORY_ABSTRACT_TOPIC = "ai-memory-abstract-refresh";
        public static final String MEMORY_ABSTRACT_CONSUMER_GROUP = "ai-memory-abstract-consumer";

        public static final String MESSAGE_TOPIC = "ai-message-dispatch";
        public static final String MESSAGE_CONSUMER_GROUP = "ai-message-consumer";
    }

    /** Milvus collection 等向量资源默认名。 */
    public static final class Milvus {
        private Milvus() {}

        public static final String USER_MEMORY_COLLECTION = "user_memory_chunk";
    }

    /** Elasticsearch 索引默认名。 */
    public static final class Elasticsearch {
        private Elasticsearch() {}

        public static final String RAG_DOCUMENT_INDEX = "rag_agent_documents";
    }
}
