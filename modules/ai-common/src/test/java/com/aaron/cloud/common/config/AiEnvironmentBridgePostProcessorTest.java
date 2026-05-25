package com.aaron.cloud.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AiEnvironmentBridgePostProcessorTest {

    @Test
    void discoveryBridgesSpringAndEureka() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("in", Map.of("ai.discovery.enabled", "true")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertTrue(Boolean.TRUE.equals(env.getProperty("spring.cloud.discovery.enabled", Boolean.class)));
        assertTrue(Boolean.TRUE.equals(env.getProperty("eureka.client.enabled", Boolean.class)));
    }

    @Test
    void discoveryOff_disablesRegisterAndFetch() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("in", Map.of("ai.discovery.enabled", "false")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertFalse(Boolean.TRUE.equals(env.getProperty("spring.cloud.discovery.enabled", Boolean.class)));
        assertFalse(Boolean.TRUE.equals(env.getProperty("eureka.client.enabled", Boolean.class)));
        assertFalse(Boolean.TRUE.equals(env.getProperty("eureka.client.register-with-eureka", Boolean.class)));
        assertFalse(Boolean.TRUE.equals(env.getProperty("eureka.client.fetch-registry", Boolean.class)));
    }

    @Test
    void redisClusterNodes_bridgedToSpringDataRedis() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "cluster",
                                        "ai.redis.cluster.nodes",
                                        "redis-a:6379, redis-b:6380",
                                        "ai.redis.cluster.max-redirects",
                                        "24")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("redis-a:6379", env.getProperty("spring.data.redis.cluster.nodes[0]"));
        assertEquals("redis-b:6380", env.getProperty("spring.data.redis.cluster.nodes[1]"));
        assertEquals(24, env.getProperty("spring.data.redis.cluster.max-redirects", Integer.class));
        assertEquals(Boolean.TRUE, env.getProperty("spring.data.redis.lettuce.cluster.refresh.adaptive", Boolean.class));
        assertEquals("30s", env.getProperty("spring.data.redis.lettuce.cluster.refresh.period"));
    }

    @Test
    void redisClusterNodes_semicolonSeparated_bridges() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "cluster",
                                        "ai.redis.cluster.nodes",
                                        "h1:6379;h2:6380")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("h1:6379", env.getProperty("spring.data.redis.cluster.nodes[0]"));
        assertEquals("h2:6380", env.getProperty("spring.data.redis.cluster.nodes[1]"));
    }

    @Test
    void redisClusterNodes_withoutMode_doesNotBridge() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(new MapPropertySource("in", Map.of("ai.redis.cluster.nodes", "redis-a:6379")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertNull(env.getProperty("spring.data.redis.cluster.nodes[0]"));
    }

    @Test
    void redisStandaloneMode_ignoresClusterNodes() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "standalone",
                                        "ai.redis.cluster.nodes",
                                        "redis-a:6379")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertNull(env.getProperty("spring.data.redis.cluster.nodes[0]"));
    }

    @Test
    void redisClusterMode_withoutNodes_throws() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("in", Map.of("ai.redis.mode", "cluster")));

        assertThrows(
                IllegalStateException.class,
                () -> new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication()));
    }

    @Test
    void redisSentinelNodes_bridgedToSpringDataRedis() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "sentinel",
                                        "ai.redis.sentinel.master",
                                        "mymaster",
                                        "ai.redis.sentinel.nodes",
                                        "s1:26379;s2:26380")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("mymaster", env.getProperty("spring.data.redis.sentinel.master"));
        assertEquals("s1:26379", env.getProperty("spring.data.redis.sentinel.nodes[0]"));
        assertEquals("s2:26380", env.getProperty("spring.data.redis.sentinel.nodes[1]"));
    }

    @Test
    void redisSentinelMode_bridgesSentinelPasswordFromSpringDataRedisPassword() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "sentinel",
                                        "ai.redis.sentinel.master",
                                        "mymaster",
                                        "ai.redis.sentinel.nodes",
                                        "s1:26379",
                                        "spring.data.redis.password",
                                        "redis-secret")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("redis-secret", env.getProperty("spring.data.redis.sentinel.password"));
    }

    @Test
    void redisSentinelMode_explicitAiSentinelPasswordOverridesDataPassword() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "sentinel",
                                        "ai.redis.sentinel.master",
                                        "mymaster",
                                        "ai.redis.sentinel.nodes",
                                        "s1:26379",
                                        "ai.redis.sentinel.password",
                                        "sentinel-only",
                                        "spring.data.redis.password",
                                        "data-only")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("sentinel-only", env.getProperty("spring.data.redis.sentinel.password"));
    }

    @Test
    void redisSentinelMode_sendAuthFalse_doesNotBridgeSentinelPassword() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "sentinel",
                                        "ai.redis.sentinel.master",
                                        "mymaster",
                                        "ai.redis.sentinel.nodes",
                                        "s1:26379",
                                        "ai.redis.sentinel.send-auth-to-sentinel",
                                        "false",
                                        "spring.data.redis.password",
                                        "redis-secret")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertNull(env.getProperty("spring.data.redis.sentinel.password"));
    }

    @Test
    void redisSentinelMode_bridgesSentinelUsernameWhenSet() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.mode",
                                        "sentinel",
                                        "ai.redis.sentinel.master",
                                        "mymaster",
                                        "ai.redis.sentinel.nodes",
                                        "s1:26379",
                                        "ai.redis.sentinel.username",
                                        "sentinel-user")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("sentinel-user", env.getProperty("spring.data.redis.sentinel.username"));
    }

    @Test
    void redisSentinelMode_withoutMaster_throws() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of("ai.redis.mode", "sentinel", "ai.redis.sentinel.nodes", "h:26379")));

        assertThrows(
                IllegalStateException.class,
                () -> new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication()));
    }

    @Test
    void redisSentinelMode_withoutNodes_throws() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in", Map.of("ai.redis.mode", "sentinel", "ai.redis.sentinel.master", "mymaster")));

        assertThrows(
                IllegalStateException.class,
                () -> new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication()));
    }

    @Test
    void redisMode_invalidValue_throws() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("in", Map.of("ai.redis.mode", "replication")));

        assertThrows(
                IllegalStateException.class,
                () -> new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication()));
    }

    @Test
    void rocketmqBridgesFromAi() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.rocketmq.name-server",
                                        "127.0.0.1:9876",
                                        "ai.rocketmq.producer-group",
                                        "my-group")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertTrue("127.0.0.1:9876".equals(env.getProperty("rocketmq.name-server")));
        assertTrue("my-group".equals(env.getProperty("rocketmq.producer.group")));
    }

    @Test
    void oauthIssuerBridgedWhenNonEmpty() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in", Map.of("ai.providers.oauth2-resource-server.jwt-issuer-uri", "https://idp.example.com")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertTrue("https://idp.example.com".equals(env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")));
    }

    @Test
    void elasticsearchHealthDefaultOff_evenWhenAiRagEsEnabled() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.rag.elasticsearch.enabled",
                                        "true",
                                        "AI_RAG_ES_ENABLED",
                                        "true")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertFalse(Boolean.TRUE.equals(env.getProperty("management.health.elasticsearch.enabled", Boolean.class)));
    }

    @Test
    void elasticsearchHealthOnWhenMgmtEnvTrue() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(new MapPropertySource("in", Map.of("MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED", "true")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertTrue(Boolean.TRUE.equals(env.getProperty("management.health.elasticsearch.enabled", Boolean.class)));
    }
}
