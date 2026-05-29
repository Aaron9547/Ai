package com.aaron.cloud.common.outbound;

import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 按出站维度获取或创建 {@link CircuitBreaker}；使用 {@code aiOutboundCircuitBreakerRegistry} 专用注册表。
 */
@Component
public class OutboundCircuitBreakerSupport {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final AiOutboundResilienceProperties properties;

    public OutboundCircuitBreakerSupport(
            @Qualifier("aiOutboundCircuitBreakerRegistry") CircuitBreakerRegistry circuitBreakerRegistry,
            AiOutboundResilienceProperties properties) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.properties = properties;
    }

    public Optional<CircuitBreaker> forStream(long tenantId, String modelAlias) {
        return forKey(OutboundKind.LLM_STREAM, tenantId + "-" + modelAlias);
    }

    public Optional<CircuitBreaker> forWebSearch(long tenantId, long modelRowId) {
        return forKey(OutboundKind.LLM_WEB_SEARCH, tenantId + "-" + modelRowId);
    }

    public Optional<CircuitBreaker> forEmbeddingHttp(long tenantId, long modelRowId) {
        return forKey(OutboundKind.LLM_EMBEDDING, tenantId + "-" + modelRowId);
    }

    public Optional<CircuitBreaker> forEmbeddingFeign(long tenantId, long modelRowId) {
        return forKey(OutboundKind.LLM_EMBEDDING_FEIGN, tenantId + "-" + modelRowId);
    }

    public Optional<CircuitBreaker> forCoze(String key) {
        return forKey(OutboundKind.INTENT_COZE, key);
    }

    public Optional<CircuitBreaker> forMcp(long tenantId, long serverId) {
        return forKey(OutboundKind.MCP, tenantId + "-" + serverId);
    }

    private Optional<CircuitBreaker> forKey(OutboundKind kind, String keySuffix) {
        if (!properties.isEnabled() || !properties.getCircuitBreaker().isEnabled()) {
            return Optional.empty();
        }
        String name = kind.name() + "-" + sanitize(keySuffix);
        return Optional.of(
                circuitBreakerRegistry.circuitBreaker(name, () -> OutboundResilienceConfiguration.toCircuitBreakerConfig(properties)));
    }

    public static boolean isCallNotPermitted(Throwable t) {
        return t instanceof CallNotPermittedException
                || t != null && t.getCause() instanceof CallNotPermittedException;
    }

    public static LlmOutboundException circuitOpen(OutboundKind kind, String modelAlias) {
        return new LlmOutboundException(
                kind,
                "CIRCUIT_OPEN",
                "上游繁忙或连续失败，已暂时熔断，请稍后重试",
                503,
                null,
                null,
                modelAlias,
                null);
    }

    public static LlmOutboundException toOutboundException(
            OutboundKind kind, String modelAlias, CallNotPermittedException e) {
        return new LlmOutboundException(
                kind,
                "CIRCUIT_OPEN",
                "上游繁忙或连续失败，已暂时熔断，请稍后重试",
                503,
                null,
                null,
                modelAlias,
                e);
    }

    static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "na";
        }
        String t = s.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    public static void sleepStreamBackoff(AiOutboundResilienceProperties props, int attemptZeroBased) {
        sleepBackoff(props.getStreamInitialBackoffMs(), props.getStreamMaxBackoffMs(), props.getJitterRatio(), attemptZeroBased);
    }

    public static void sleepSyncBackoff(AiOutboundResilienceProperties props, int attemptZeroBased) {
        sleepBackoff(props.getSyncInitialBackoffMs(), props.getSyncMaxBackoffMs(), props.getJitterRatio(), attemptZeroBased);
    }

    private static void sleepBackoff(long baseMs, long maxMs, double jitter, int attemptZeroBased) {
        long exp = Math.min(maxMs, baseMs * (1L << Math.min(attemptZeroBased, 8)));
        long delta =
                jitter <= 0
                        ? 0
                        : ThreadLocalRandom.current()
                                .nextLong(0, 1 + (long) (exp * jitter));
        long sleep = Math.min(maxMs, exp + delta);
        try {
            Thread.sleep(Math.max(1, sleep));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
