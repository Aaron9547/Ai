package com.aaron.cloud.common.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * 将分散在官方前缀下的键收束到 {@code application.yml} 的 {@code ai.*} 唯一定义，在环境准备阶段写入 Spring/RocketMQ/Management 等所需键，
 * 避免新人同一语义在多处改占位。各桥接段见 {@link #postProcessEnvironment} 内注释。
 * <p>Actuator 的 {@code management.health.elasticsearch.enabled} 仅由 {@code MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED} 桥接（默认 false），与
 * {@code AI_RAG_ES_ENABLED} / RAG ES 客户端开关解耦，避免已开 RAG ES 但集群未就绪时出现 {@code ElasticsearchRestClientHealthIndicator} 反复 WARN。
 */
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AiEnvironmentBridgePostProcessor implements EnvironmentPostProcessor {

    /** 非 Redis exclude 的桥接键统一放此 PropertySource */
    public static final String BRIDGE_PROPERTY_SOURCE_NAME = "ai-environment-bridge";

    /** 与历史 Redis 测试、排障约定一致 */
    public static final String REDIS_EXCLUDE_PROPERTY_SOURCE_NAME = "ai-redis-toggle-excludes";

    private static final String EXCLUDE_KEY = "spring.autoconfigure.exclude";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var sources = environment.getPropertySources();
        sources.remove(BRIDGE_PROPERTY_SOURCE_NAME);
        sources.remove(REDIS_EXCLUDE_PROPERTY_SOURCE_NAME);

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

        if (!bridge.isEmpty()) {
            sources.addFirst(new MapPropertySource(BRIDGE_PROPERTY_SOURCE_NAME, bridge));
        }

        // 5) Redis：唯一定义 ai.redis.enabled；false 时排除自动配置（连接参数仍在 spring.data.redis.*）
        boolean redisOn = environment.getProperty("ai.redis.enabled", Boolean.class, true);
        if (!redisOn) {
            Set<String> excludes = new LinkedHashSet<>();
            String existing = environment.getProperty(EXCLUDE_KEY);
            if (StringUtils.hasText(existing)) {
                for (String part : existing.split(",")) {
                    String t = part.trim();
                    if (StringUtils.hasText(t)) {
                        excludes.add(t);
                    }
                }
            }
            excludes.add(RedisAutoConfiguration.class.getName());
            excludes.add(RedisRepositoriesAutoConfiguration.class.getName());
            sources.addFirst(
                    new MapPropertySource(
                            REDIS_EXCLUDE_PROPERTY_SOURCE_NAME,
                            Map.of(EXCLUDE_KEY, String.join(",", excludes))));
        }
    }
}
