package com.aaron.cloud.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.rag")
public class AiRagProperties {

    /**
     * 取值见 {@link com.aaron.cloud.common.api.enums.RagRetrievalMode}（yaml：{@code milvus} /
     * {@code milvus_es_hybrid}）。
     */
    private String retrievalMode = "milvus";

    private final Elasticsearch elasticsearch = new Elasticsearch();

    /**
     * 向量「本地部署」经 Feign 转发到 RAG 侧网关：{@code POST {baseUrl}/{tenantCode}/privateModel/embedding}，
     * {@code tenantCode} 为 {@code sys_tenant.code}。向量模型的 Base URL / 模型名 / Key 等在库表 {@code llm_model}（VECTOR），由
     * {@link com.aaron.cloud.rag.RagEmbeddingService} 读取，不在此 yml 配置。
     */
    private final LocalEmbedFeign localEmbedFeign = new LocalEmbedFeign();

    public com.aaron.cloud.common.api.enums.RagRetrievalMode resolvedRetrievalMode() {
        return com.aaron.cloud.common.api.enums.RagRetrievalMode.fromYaml(retrievalMode);
    }

    /** 与 ly-ai-rag-svc {@code elasticsearch} 块同形：{@code enabled} 总闸；节点与账号在 {@link Elasticsearch#config}。 */
    @Data
    public static class Elasticsearch {
        private boolean enabled = false;
        private Config config = new Config();
        /** 与索引模板一致；需自行写入 chunk 文档（tenant_id、kb_id、content）。 */
        private String indexName = "ai_rag_chunk";
        /**
         * 非空时对 ES 使用 HTTP Basic；否则使用 {@link Config} 内 {@code userName}/{@code password}（与 RAG 侧
         * {@code elasticsearch.username} 覆盖 {@code config.userName} 一致）。
         */
        private String username = "";
        private String password = "";

        @Data
        public static class Config {
            /** 与 RAG 侧展示一致；Rest 客户端不使用，仅配置占位。 */
            private String clusterName = "";
            /** 与 ly-ai-rag-svc {@code elasticsearch.config.hostPorts} 同形（{@code host:port;host:port}）。 */
            private String hostPorts = "";
            private String userName = "";
            private String password = "";
        }
    }

    @Data
    public static class LocalEmbedFeign {
        /**
         * 与 ly-ai-rag {@code aiengine.domain} 根路径一致（如 {@code http://host/ly-ai-rag}）；非空则 Feign 直连。环境变量：
         * {@code AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL} 或 {@code AI_RAG_ENGINE_BASE_URL}（见 {@code application.yml} 占位）。
         */
        private String baseUrl = "";

        /** Eureka 注册名；与 {@link com.aaron.cloud.rag.remote.RagLocalEmbeddingFeignClient} 占位一致。 */
        private String serviceId = "ly-ai-rag-svc";
    }
}
