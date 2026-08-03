package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelStreamResult;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.outbound.OutboundCircuitBreakerSupport;
import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import com.aaron.cloud.common.outbound.OutboundMetricsSupport;
import com.aaron.cloud.common.outbound.OutboundRetrySupport;
import com.aaron.cloud.common.outbound.OutboundTenantUpstreamQuarantine;
import com.aaron.cloud.model.remote.RemoteModelClient;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
public class RemoteModelAdapter implements ModelInvokePort {

    private final RemoteModelClient remoteModelClient;
    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;
    private final OutboundCircuitBreakerSupport outboundCircuitBreakerSupport;
    private final OutboundMetricsSupport outboundMetricsSupport;
    private final OutboundTenantUpstreamQuarantine outboundTenantUpstreamQuarantine;

    @Override
    public void streamCompletion(ModelChatRequest request, java.util.function.Consumer<String> onToken)
            throws Exception {
        long tid = resolveTenantId(request);
        outboundTenantUpstreamQuarantine.assertStreamAllowed(tid);
        AiOutboundResilienceProperties props = outboundResilienceRuntime.effective(tid);
        String alias =
                request.getModelAlias() == null || request.getModelAlias().isBlank()
                        ? "na"
                        : request.getModelAlias().trim();
        Optional<CircuitBreaker> cb = outboundCircuitBreakerSupport.forStream(tid, alias);
        long t0 = System.nanoTime();
        if (cb.isPresent() && !cb.get().tryAcquirePermission()) {
            outboundMetricsSupport.recordError(OutboundKind.LLM_STREAM, "CIRCUIT_OPEN", 503);
            throw OutboundCircuitBreakerSupport.circuitOpen(OutboundKind.LLM_STREAM, alias);
        }
        try {
            String text =
                    OutboundRetrySupport.executeSyncWithRetry(
                            props,
                            OutboundKind.LLM_STREAM,
                            outboundMetricsSupport,
                            props.getSyncMaxAttempts() + 1,
                            () -> remoteModelClient.completion(request),
                            ex -> {
                                if (ex instanceof FeignException fe) {
                                    return OutboundRetrySupport.isRetryableHttpStatus(
                                            props, fe.status());
                                }
                                return OutboundRetrySupport.isRetryableException(props, ex);
                            });
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    onToken.accept(String.valueOf(text.charAt(i)));
                }
            }
            if (cb.isPresent()) {
                cb.get().onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS);
            }
            outboundTenantUpstreamQuarantine.recordTenantSuccess(tid);
        } catch (Exception e) {
            if (cb.isPresent()) {
                cb.get().onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e);
            }
            outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tid, e);
            if (!(e instanceof IllegalArgumentException)) {
                String code =
                        e instanceof FeignException fe ? "HTTP_" + fe.status() : e.getClass().getSimpleName();
                Integer http = e instanceof FeignException fe ? fe.status() : null;
                outboundMetricsSupport.recordError(OutboundKind.LLM_STREAM, code, http);
            }
            throw e;
        }
    }

    @Override
    public ModelStreamResult streamCompletionWithResult(
            ModelChatRequest request, java.util.function.Consumer<String> onToken) throws Exception {
        StringBuilder buf = new StringBuilder();
        streamCompletion(request, t -> {
            buf.append(t);
            onToken.accept(t);
        });
        return ModelStreamResult.builder().content(buf.toString()).finishReason("stop").build();
    }

    private static long resolveTenantId(ModelChatRequest request) {
        Long tid = request.getTenantId();
        if (tid == null) {
            var snap = TenantContextHolder.getOrNull();
            if (snap != null) {
                tid = snap.getTenantId();
            }
        }
        return tid == null ? 0L : tid;
    }
}
