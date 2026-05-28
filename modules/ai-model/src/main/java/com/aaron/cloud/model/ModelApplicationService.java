package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelTokenUsage;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.outbound.LlmOutboundException;
import com.aaron.cloud.common.outbound.OutboundCircuitBreakerSupport;
import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import com.aaron.cloud.common.outbound.OutboundMetricsSupport;
import com.aaron.cloud.common.outbound.OutboundTenantUpstreamQuarantine;
import com.aaron.cloud.model.metering.ModelStreamUsageRecorder;
import com.aaron.cloud.model.spi.ModelCompletionEngine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelApplicationService {

    private final ModelCompletionEngine modelCompletionEngine;
    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;
    private final OutboundCircuitBreakerSupport outboundCircuitBreakerSupport;
    private final OutboundMetricsSupport outboundMetricsSupport;
    private final OutboundTenantUpstreamQuarantine outboundTenantUpstreamQuarantine;
    private final ModelStreamUsageRecorder modelStreamUsageRecorder;

    public void streamCompletion(ModelChatRequest request, Consumer<String> onToken) throws Exception {
        long wallStart = System.currentTimeMillis();
        long tenantId = resolveTenantId(request);
        outboundTenantUpstreamQuarantine.assertStreamAllowed(tenantId);
        AtomicReference<ModelTokenUsage> usageRef = new AtomicReference<>();
        modelStreamUsageRecorder.attachUsageCapture(request, usageRef);

        if (!outboundResilienceRuntime.effective(tenantId).isEnabled()) {
            try {
                modelCompletionEngine.streamCompletion(request, onToken);
                outboundTenantUpstreamQuarantine.recordTenantSuccess(tenantId);
                logCompletion(wallStart, request);
                modelStreamUsageRecorder.recordAfterStream(
                        request, usageRef.get(), System.currentTimeMillis() - wallStart);
            } catch (Exception e) {
                throw e;
            }
            return;
        }
        String alias =
                request.getModelAlias() == null || request.getModelAlias().isBlank()
                        ? "mock"
                        : request.getModelAlias().trim();
        Optional<CircuitBreaker> cb = outboundCircuitBreakerSupport.forStream(tenantId, alias);
        Timer.Sample timer = outboundMetricsSupport.startTimer();
        long t0 = System.nanoTime();
        if (cb.isPresent() && !cb.get().tryAcquirePermission()) {
            outboundMetricsSupport.recordError(OutboundKind.LLM_STREAM, "CIRCUIT_OPEN", 503);
            outboundMetricsSupport.stopStreamTimer(timer, OutboundKind.LLM_STREAM, false);
            throw OutboundCircuitBreakerSupport.circuitOpen(OutboundKind.LLM_STREAM, alias);
        }
        try {
            modelCompletionEngine.streamCompletion(request, onToken);
            if (cb.isPresent()) {
                cb.get().onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            }
            outboundMetricsSupport.stopStreamTimer(timer, OutboundKind.LLM_STREAM, true);
            outboundTenantUpstreamQuarantine.recordTenantSuccess(tenantId);
            logCompletion(wallStart, request);
            modelStreamUsageRecorder.recordAfterStream(
                    request, usageRef.get(), System.currentTimeMillis() - wallStart);
        } catch (Exception e) {
            if (cb.isPresent()) {
                cb.get().onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e);
            }
            outboundMetricsSupport.stopStreamTimer(timer, OutboundKind.LLM_STREAM, false);
            if (!(e instanceof IllegalArgumentException)) {
                String code =
                        e instanceof LlmOutboundException lo ? lo.getCode() : e.getClass().getSimpleName();
                Integer http = e instanceof LlmOutboundException lo ? lo.getHttpStatus() : null;
                outboundMetricsSupport.recordError(OutboundKind.LLM_STREAM, code, http);
            }
            var snap = TenantContextHolder.getOrNull();
            log.error(
                    "model.streamCompletion failed tenantId={} userId={} deviceId={} modelAlias={} thinking={} {}",
                    snap != null ? snap.getTenantId() : request.getTenantId(),
                    snap != null ? snap.getUserId() : request.getUserId(),
                    snap != null ? snap.getDeviceId() : request.getDeviceId(),
                    request.getModelAlias(),
                    request.getThinkingEnabled(),
                    summarizeModelRequest(request),
                    e);
            throw e;
        }
    }

    private static long resolveTenantId(ModelChatRequest request) {
        Long tenantId = request.getTenantId();
        if (tenantId == null) {
            var snap = TenantContextHolder.getOrNull();
            if (snap != null) {
                tenantId = snap.getTenantId();
            }
        }
        return tenantId == null ? 0L : tenantId;
    }

    private void logCompletion(long wallStart, ModelChatRequest request) {
        long durationMs = System.currentTimeMillis() - wallStart;
        var snap = TenantContextHolder.getOrNull();
        log.info(
                "model.completion tenantId={} deviceId={} durationMs={} modelAlias={}",
                snap != null ? snap.getTenantId() : request.getTenantId(),
                snap != null ? snap.getDeviceId() : request.getDeviceId(),
                durationMs,
                request.getModelAlias());
    }

    private static String summarizeModelRequest(ModelChatRequest request) {
        var turns = request.getMessages();
        int n = turns == null ? 0 : turns.size();
        if (n == 0) {
            return "turns=0 roles=[]";
        }
        StringBuilder roles = new StringBuilder();
        int cap = Math.min(n, 12);
        for (int i = 0; i < cap; i++) {
            if (i > 0) {
                roles.append(',');
            }
            var t = turns.get(i);
            roles.append(t.getRole() == null ? "?" : t.getRole());
        }
        if (n > cap) {
            roles.append(",…");
        }
        return "turns=" + n + " roles=[" + roles + "] (content omitted)";
    }
}
