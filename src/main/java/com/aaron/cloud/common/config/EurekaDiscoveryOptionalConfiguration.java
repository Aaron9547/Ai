package com.aaron.cloud.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;

/**
 * 仅在 {@code ai.discovery.enabled=true} 时启用 Eureka 发现客户端；避免主类上无条件 {@link EnableDiscoveryClient} 在部分环境下
 * 与「关闭发现」心智不一致。关闭时由 {@link AiEnvironmentBridgePostProcessor} 写入 {@code eureka.client.enabled=false} 等。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai.discovery", name = "enabled", havingValue = "true")
@EnableDiscoveryClient
public class EurekaDiscoveryOptionalConfiguration {}
