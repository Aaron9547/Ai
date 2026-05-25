package com.aaron.cloud.rag;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 与 {@link ElasticsearchRagSearchClient} 装配条件一致：{@code enabled=true} 且 {@code config.host-ports} 非空（与对端
 * {@code elasticsearch.config.hostPorts} 一致）。
 */
public class ElasticsearchRagClientEnabledCondition implements Condition {

    private static final String P_ENABLED = "ai.rag.elasticsearch.enabled";
    private static final String P_CONFIG_HOST_PORTS = "ai.rag.elasticsearch.config.host-ports";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        if (!Boolean.parseBoolean(env.getProperty(P_ENABLED, "false"))) {
            return false;
        }
        return nonBlank(env.getProperty(P_CONFIG_HOST_PORTS, ""));
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
