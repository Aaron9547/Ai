package com.aaron.cloud.common.outbound;

import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/** 同步出站调用的重试与可重试性判断；流式整请求重试在 {@link com.aaron.cloud.model.openai.OpenAiChatStreamClient} 使用同类规则。 */
@Slf4j
public final class OutboundRetrySupport {

    private OutboundRetrySupport() {}

    @FunctionalInterface
    public interface SyncCall<T> {
        T call() throws Exception;
    }

    public static <T> T executeSyncWithRetry(
            AiOutboundResilienceProperties props,
            OutboundKind kind,
            OutboundMetricsSupport metrics,
            int maxAttemptsInclusiveFirst,
            SyncCall<T> call,
            Predicate<Exception> isRetryable)
            throws Exception {
        int maxRetries = Math.max(0, maxAttemptsInclusiveFirst - 1);
        Exception last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return call.call();
            } catch (Exception e) {
                last = e;
                if (attempt == maxRetries || !isRetryable.test(e)) {
                    throw e;
                }
                if (metrics != null) {
                    metrics.recordRetry(kind);
                }
                OutboundCircuitBreakerSupport.sleepSyncBackoff(props, attempt);
            }
        }
        throw last != null ? last : new IllegalStateException("retry exhausted");
    }

    public static boolean isRetryableException(AiOutboundResilienceProperties props, Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof IllegalArgumentException) {
            return false;
        }
        if (t instanceof HttpTimeoutException || t instanceof TimeoutException) {
            return true;
        }
        if (t instanceof IOException) {
            return true;
        }
        Throwable c = t.getCause();
        if (c != null && c != t) {
            return isRetryableException(props, c);
        }
        return false;
    }

    public static boolean isRetryableHttpStatus(AiOutboundResilienceProperties props, int status) {
        return props.isRetryableHttpStatus(status);
    }

    public static Long parseRetryAfterSeconds(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String s = headerValue.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
