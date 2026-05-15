package com.aaron.cloud.common.outbound;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class OutboundMetricsSupport {

    private final MeterRegistry meterRegistry;

    public OutboundMetricsSupport(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordError(OutboundKind kind, String code, Integer httpStatus) {
        if (meterRegistry == null) {
            return;
        }
        MeterRegistry reg = meterRegistry;
        String http = httpStatus == null ? "none" : String.valueOf(httpStatus);
        String c = code == null ? "unknown" : code.replaceAll("[^a-zA-Z0-9._-]", "_");
        Counter.builder("llm_upstream_errors_total")
                .tag("kind", kind.name())
                .tag("code", c.length() > 48 ? c.substring(0, 48) : c)
                .tag("http", http)
                .register(reg)
                .increment();
    }

    public void recordRetry(OutboundKind kind) {
        if (meterRegistry == null) {
            return;
        }
        MeterRegistry reg = meterRegistry;
        Counter.builder("llm_upstream_retries_total").tag("kind", kind.name()).register(reg).increment();
    }

    public Timer.Sample startTimer() {
        if (meterRegistry == null) {
            return null;
        }
        return Timer.start(meterRegistry);
    }

    public void stopStreamTimer(Timer.Sample sample, OutboundKind kind, boolean success) {
        if (sample == null || meterRegistry == null) {
            return;
        }
        Timer timer =
                Timer.builder("llm_upstream_latency")
                        .tag("kind", kind.name())
                        .tag("outcome", success ? "success" : "error")
                        .register(meterRegistry);
        sample.stop(timer);
    }

    public void recordDurationNanos(OutboundKind kind, boolean success, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder("llm_upstream_latency")
                .tag("kind", kind.name())
                .tag("outcome", success ? "success" : "error")
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
