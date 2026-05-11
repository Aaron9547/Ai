package com.aaron.cloud.rag.remote;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 在下列任一成立时注册本地嵌入 Feign：配置了非空 {@code ai.rag.local-embed-feign.base-url}（直连）；或 {@code ai.discovery.enabled}
 * 为 true 且配置了非空 {@code ai.rag.local-embed-feign.service-id}（经 Eureka + LoadBalancer，与 {@code ly-ai-rag-svc} 等注册名对齐）。
 */
public final class RagLocalEmbeddingFeignCondition implements Condition {

    private static final String PROP_BASE_URL = "ai.rag.local-embed-feign.base-url";
    private static final String PROP_SERVICE_ID = "ai.rag.local-embed-feign.service-id";
    /** 与 {@code application.yml} 中台总闸一致；勿直接读 spring.cloud.*，避免与 Eureka 双键认知分裂。 */
    private static final String PROP_DISCOVERY = "ai.discovery.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String baseUrl = context.getEnvironment().getProperty(PROP_BASE_URL, "");
        if (StringUtils.hasText(baseUrl)) {
            return true;
        }
        boolean discovery =
                Boolean.parseBoolean(context.getEnvironment().getProperty(PROP_DISCOVERY, "false"));
        String serviceId = context.getEnvironment().getProperty(PROP_SERVICE_ID, "");
        return discovery && StringUtils.hasText(serviceId);
    }
}
