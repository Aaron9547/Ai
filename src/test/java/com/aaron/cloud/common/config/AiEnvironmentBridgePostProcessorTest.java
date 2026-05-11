package com.aaron.cloud.common.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
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
    void redisOff_mergesExclude() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "in",
                                Map.of(
                                        "ai.redis.enabled",
                                        "false",
                                        "spring.autoconfigure.exclude",
                                        "com.example.LegacyAutoConfig")));

        new AiEnvironmentBridgePostProcessor().postProcessEnvironment(env, new SpringApplication());

        String ex = env.getProperty("spring.autoconfigure.exclude");
        assertTrue(ex.contains("com.example.LegacyAutoConfig"));
        assertTrue(ex.contains(RedisAutoConfiguration.class.getName()));
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
