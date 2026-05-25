package com.aaron.cloud.common.config.properties;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
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

    /** 站点爬取执行阶段 Redis 锁 TTL（一次性与 job 执行共用）。 */
    private final SiteCrawl siteCrawl = new SiteCrawl();

    /** 租户通用定时任务调度（扫描 ten_scheduled_task）。 */
    private final ScheduledTasks scheduledTasks = new ScheduledTasks();

    public RagRetrievalMode resolvedRetrievalMode() {
        return RagRetrievalMode.fromYaml(retrievalMode);
    }

    /** 与对端 {@code elasticsearch} 块同形：{@code enabled} 总闸；节点与账号在 {@link Elasticsearch#config}。 */
    @Data
    public static class Elasticsearch {
        private boolean enabled = false;
        private Config config = new Config();
        /** 与对端默认 {@code elasticsearch.index-name} 一致（rag_agent_documents）。 */
        private String indexName = "rag_agent_documents";
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
            /** 与对端 {@code elasticsearch.config.hostPorts} 同形（{@code host:port;host:port}）。 */
            private String hostPorts = "";
            private String userName = "";
            private String password = "";
        }
    }

    @Data
    public static class LocalEmbedFeign {
        /**
         * 与对端网关根地址（如 {@code aiengine.domain}）一致（含路径前缀与尾斜杠）；非空则 Feign 直连。环境变量：
         * {@code AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL} 或 {@code AI_RAG_ENGINE_BASE_URL}（见 {@code application.yml} 占位）。
         */
        private String baseUrl = "";

        /** Eureka 注册名；与 {@link com.aaron.cloud.rag.remote.RagLocalEmbeddingFeignClient} 占位一致。 */
        private String serviceId = "rag-embedding-svc";
    }

    @Data
    public static class SiteCrawl {
        /**
         * 单次站点爬取 Redis 分布式锁 TTL（秒），key {@code ai:lock:scheduled-task:run:*} /
         * {@code ai:lock:scheduled-task:site:*}；默认 6 小时。
         */
        private long crawlLockTtlSeconds = 21_600L;

        /** 正文抽取：jsoup（默认）或 readability。 */
        private String contentExtractor = "jsoup";

        /** 预览接口单页最多返回分片条数。 */
        private int previewMaxChunks = 20;

        /** 预览拉取 HTML 最大字节。 */
        private int previewMaxBodyBytes = 512_000;

        /** 站点预览抽样 URL 数。 */
        private int previewSiteSampleUrls = 3;
    }

    @Data
    public static class ScheduledTasks {
        /** 是否启用统一调度 tick。 */
        private boolean enabled = true;

        /** Spring {@code @Scheduled} cron（6 段）：扫描 ten_scheduled_task 是否到期。 */
        private String pollCron = "0 * * * * *";

        /** 调度 tick 全局锁 TTL（秒），key {@code ai:lock:scheduled-task:poller:tick}。 */
        private long pollerLockTtlSeconds = 55L;

        /** 单条注册项/站点入队锁 TTL（秒）。 */
        private long taskLockTtlSeconds = 300L;
    }
}
