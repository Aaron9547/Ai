package com.aaron.cloud.rag.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * {@code ai.rag.local-embed-feign.base-url} 非空（直连；可由 {@code AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL} 或 {@code AI_RAG_ENGINE_BASE_URL} 注入），或
 * {@code ai.discovery.enabled=true} 且配置了 {@code ai.rag.local-embed-feign.service-id}（Eureka + LB）时启用本地嵌入 Feign。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.cloud.openfeign.FeignAutoConfiguration")
@Conditional(RagLocalEmbeddingFeignCondition.class)
@EnableFeignClients(clients = RagLocalEmbeddingFeignClient.class)
public class RagLocalEmbeddingFeignAutoConfiguration {}
