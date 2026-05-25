package com.aaron.cloud.common.config.properties;

import com.aaron.cloud.common.api.enums.infra.AuthProviderMode;
import com.aaron.cloud.common.api.enums.infra.FileStorageProviderMode;
import com.aaron.cloud.common.api.enums.infra.NotificationProviderMode;
import com.aaron.cloud.common.api.enums.infra.VectorStoreProviderMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "ai.providers")
public class AiProvidersProperties {

    /** permit | jwt-local | oauth2-resource */
    private String auth = "permit";

    /**
     * OAuth2 Resource Server issuer；唯一定义处，由 {@link com.aaron.cloud.common.config.AiEnvironmentBridgePostProcessor} 写入
     * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}。env：{@code AI_OAUTH2_JWT_ISSUER_URI}。
     */
    @NestedConfigurationProperty
    private Oauth2ResourceServer oauth2ResourceServer = new Oauth2ResourceServer();

    @Data
    public static class Oauth2ResourceServer {
        /** JWT issuer-uri；空则不在 Spring Security 下注册 issuer（jwt-local 等模式） */
        private String jwtIssuerUri = "";
    }

    /** local | milvus */
    private String vectorStore = "local";

    /** local | minio */
    private String fileStorage = "local";

    /** local | http（http 为占位：显式 HTTP 投递扩展点） */
    private String notification = "local";

    /**
     * 为 true 时：在启用服务发现且 Eureka 可解析时，Milvus / MinIO 等优先用 Eureka 上注册的实例地址；
     * 无可用实例时退回各子块中的 host/endpoint。与 {@code ai.discovery.enabled}（注册总闸）独立：仅由 {@code AI_INFRA_VIA_DISCOVERY} / YAML
     * {@code ai.providers.infra-via-discovery} 控制，默认 false。
     */
    private boolean infraViaDiscovery = false;

    private final Milvus milvus = new Milvus();
    private final Minio minio = new Minio();

    public AuthProviderMode resolvedAuth() {
        return AuthProviderMode.fromYaml(auth);
    }

    public VectorStoreProviderMode resolvedVectorStore() {
        return VectorStoreProviderMode.fromYaml(vectorStore);
    }

    public FileStorageProviderMode resolvedFileStorage() {
        return FileStorageProviderMode.fromYaml(fileStorage);
    }

    public NotificationProviderMode resolvedNotification() {
        return NotificationProviderMode.fromYaml(notification);
    }

    @Data
    public static class Milvus {
        /** Eureka 上的服务名（infraViaDiscovery 时优先）；默认 milvus */
        private String serviceId = "milvus";

        private String host = "127.0.0.1";
        private int port = 19530;
        private String token = "";
        private String database = "default";
        private boolean secure = false;
        private int vectorDimension = 128;
    }

    @Data
    public static class Minio {
        /** Eureka 上的服务名（infraViaDiscovery 时优先）；默认 minio */
        private String serviceId = "minio";

        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String region = "";
    }
}
