package com.aaron.cloud.common.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * 将分散在官方前缀下的键收束到 {@code application.yml} 的 {@code ai.*} 唯一定义，在环境准备阶段写入 Spring/RocketMQ/Management 等所需键，
 * 避免新人同一语义在多处改占位。各桥接段见 {@link #postProcessEnvironment} 内注释。
 * <p>本类须在 {@code META-INF/spring.factories} 中登记为 {@link org.springframework.boot.env.EnvironmentPostProcessor}（见 Spring Boot 参考手册「Customize the Environment」）。
 * <p>Redis：{@code ai.redis.mode} 为 {@code cluster} 时桥接 {@code spring.data.redis.cluster.nodes}；为 {@code sentinel} 时桥接
 * {@code spring.data.redis.sentinel.master}、{@code sentinel.nodes}，并在开启 {@code ai.redis.sentinel.send-auth-to-sentinel} 时写入
 * {@code spring.data.redis.sentinel.password}（优先 {@code ai.redis.sentinel.password}，否则回退 {@code spring.data.redis.password}，解决 Sentinel requirepass 下 NOAUTH HELLO）。
 * <p>Actuator 的 {@code management.health.elasticsearch.enabled} 仅由 {@code MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED} 桥接（默认 false），与
 * {@code AI_RAG_ES_ENABLED} / RAG ES 客户端开关解耦，避免已开 RAG ES 但集群未就绪时出现 {@code ElasticsearchRestClientHealthIndicator} 反复 WARN。
 */
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AiEnvironmentBridgePostProcessor implements EnvironmentPostProcessor {

    /** 非 Redis exclude 的桥接键统一放此 PropertySource */
    public static final String BRIDGE_PROPERTY_SOURCE_NAME = "ai-environment-bridge";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var sources = environment.getPropertySources();
        sources.remove(BRIDGE_PROPERTY_SOURCE_NAME);

        Map<String, Object> bridge = new LinkedHashMap<>();

        // 1) 服务发现：唯一定义 ai.discovery.enabled → spring.cloud.discovery + eureka.client.*（关闭时显式禁止注册/拉取，避免仅关注解仍连 defaultZone）
        boolean discoveryOn = environment.getProperty("ai.discovery.enabled", Boolean.class, false);
        bridge.put("spring.cloud.discovery.enabled", discoveryOn);
        bridge.put("eureka.client.enabled", discoveryOn);
        if (!discoveryOn) {
            bridge.put("eureka.client.register-with-eureka", false);
            bridge.put("eureka.client.fetch-registry", false);
        }

        // 2) OAuth2 Resource Server issuer：唯一定义 ai.providers.oauth2-resource-server.jwt-issuer-uri（env：AI_OAUTH2_JWT_ISSUER_URI）
        String issuer =
                environment.getProperty("ai.providers.oauth2-resource-server.jwt-issuer-uri", "");
        if (StringUtils.hasText(issuer)) {
            bridge.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer);
        }

        // 3) ES Actuator health：默认关；仅 MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=true 显式开启（与 AI_RAG_ES_ENABLED / RAG 装配解耦）
        boolean esHealth =
                Boolean.TRUE.equals(
                        environment.getProperty("MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED", Boolean.class, false));
        bridge.put("management.health.elasticsearch.enabled", esHealth);

        // 4) RocketMQ 官方键：唯一定义在 ai.rocketmq.name-server / ai.rocketmq.producer-group
        String mqNs = environment.getProperty("ai.rocketmq.name-server", "");
        if (StringUtils.hasText(mqNs)) {
            bridge.put("rocketmq.name-server", mqNs);
        }
        String mqGroup = environment.getProperty("ai.rocketmq.producer-group", "");
        if (StringUtils.hasText(mqGroup)) {
            bridge.put("rocketmq.producer.group", mqGroup);
        }

        // 5) Redis：ai.redis.mode → Spring Data Lettuce（与对端 spring.redis 26379 段对应关系：对端为 Sentinel 入口，本处 mode=sentinel 桥接 sentinel.nodes）。
        String redisMode = resolveRedisMode(environment);
        if ("cluster".equals(redisMode)) {
            applyRedisClusterBridge(environment, bridge);
        } else if ("sentinel".equals(redisMode)) {
            applyRedisSentinelBridge(environment, bridge);
        } else if (StringUtils.hasText(redisMode) && !"standalone".equals(redisMode)) {
            throw new IllegalStateException(
                    "ai.redis.mode 仅支持 standalone、cluster、sentinel（当前值: "
                            + environment.getProperty("ai.redis.mode", "").trim()
                            + "），或通过 AI_REDIS_MODE 设置");
        }

        if (!bridge.isEmpty()) {
            sources.addFirst(new MapPropertySource(BRIDGE_PROPERTY_SOURCE_NAME, bridge));
        }
    }

    static String resolveRedisMode(ConfigurableEnvironment environment) {
        String mode = environment.getProperty("ai.redis.mode", "");
        if (!StringUtils.hasText(mode)) {
            return "";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private static void applyRedisClusterBridge(ConfigurableEnvironment environment, Map<String, Object> bridge) {
        String redisClusterNodes = environment.getProperty("ai.redis.cluster.nodes", "");
        if (!StringUtils.hasText(redisClusterNodes)) {
            throw new IllegalStateException(
                    "ai.redis.mode=cluster 时必须配置 ai.redis.cluster.nodes（或环境变量 AI_REDIS_CLUSTER_NODES），"
                            + "逗号或分号分隔 host:port，例如 192.168.1.1:6379,192.168.1.2:6379");
        }
        int idx = 0;
        String normalized = redisClusterNodes.replace(';', ',');
        for (String part : normalized.split(",")) {
            String n = part.trim();
            if (StringUtils.hasText(n)) {
                bridge.put("spring.data.redis.cluster.nodes[" + idx + "]", n);
                idx++;
            }
        }
        if (idx == 0) {
            throw new IllegalStateException(
                    "ai.redis.cluster.nodes 解析后为空，请使用逗号或分号分隔 host:port，例如 192.168.1.1:6379,192.168.1.2:6379");
        }
        int maxRedirects = environment.getProperty("ai.redis.cluster.max-redirects", Integer.class, 16);
        bridge.put("spring.data.redis.cluster.max-redirects", maxRedirects);
        bridge.put("spring.data.redis.lettuce.cluster.refresh.adaptive", true);
        bridge.put("spring.data.redis.lettuce.cluster.refresh.period", "30s");
    }

    /**
     * 对端常见 {@code spring.redis.port=26379} 与 {@code hostPort} 多节点为 <strong>Sentinel</strong> 拓扑（非 Redis Cluster 数据端口）。
     * 本工程通过 {@code ai.redis.mode=sentinel} 写入 {@code spring.data.redis.sentinel.*}，由 Lettuce 经 Sentinel 发现主库再读写。
     */
    private static void applyRedisSentinelBridge(ConfigurableEnvironment environment, Map<String, Object> bridge) {
        String master = environment.getProperty("ai.redis.sentinel.master", "");
        if (!StringUtils.hasText(master)) {
            throw new IllegalStateException(
                    "ai.redis.mode=sentinel 时必须配置 ai.redis.sentinel.master（或环境变量 AI_REDIS_SENTINEL_MASTER），"
                            + "与现网 Sentinel monitor 名一致（常见为 mymaster）");
        }
        String sentinelNodes = environment.getProperty("ai.redis.sentinel.nodes", "");
        if (!StringUtils.hasText(sentinelNodes)) {
            throw new IllegalStateException(
                    "ai.redis.mode=sentinel 时必须配置 ai.redis.sentinel.nodes（或环境变量 AI_REDIS_SENTINEL_NODES），"
                            + "逗号或分号分隔 Sentinel host:port，例如 192.168.1.1:26379;192.168.1.2:26379");
        }
        bridge.put("spring.data.redis.sentinel.master", master.trim());
        int idx = 0;
        String normalized = sentinelNodes.replace(';', ',');
        for (String part : normalized.split(",")) {
            String n = part.trim();
            if (StringUtils.hasText(n)) {
                bridge.put("spring.data.redis.sentinel.nodes[" + idx + "]", n);
                idx++;
            }
        }
        if (idx == 0) {
            throw new IllegalStateException(
                    "ai.redis.sentinel.nodes 解析后为空，请使用逗号或分号分隔 host:port，例如 192.168.37.17:26379");
        }
        // Sentinel 端口若启用 requirepass（Redis 6+ 常见），Lettuce 须在 HELLO 前 AUTH；仅 spring.data.redis.password 不够。
        boolean sendAuth =
                environment.getProperty("ai.redis.sentinel.send-auth-to-sentinel", Boolean.class, true);
        if (sendAuth) {
            String sentinelPassword = environment.getProperty("ai.redis.sentinel.password", "");
            if (!StringUtils.hasText(sentinelPassword)) {
                sentinelPassword = environment.getProperty("spring.data.redis.password", "");
            }
            if (StringUtils.hasText(sentinelPassword)) {
                bridge.put("spring.data.redis.sentinel.password", sentinelPassword);
            }
        }
        String sentinelUsername = environment.getProperty("ai.redis.sentinel.username", "");
        if (StringUtils.hasText(sentinelUsername)) {
            bridge.put("spring.data.redis.sentinel.username", sentinelUsername.trim());
        }
    }
}
