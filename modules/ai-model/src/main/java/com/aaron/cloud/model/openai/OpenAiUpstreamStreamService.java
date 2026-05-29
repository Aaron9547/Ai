package com.aaron.cloud.model.openai;

import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.outbound.LlmOutboundException;
import com.aaron.cloud.common.outbound.OutboundCircuitBreakerSupport;
import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import com.aaron.cloud.common.outbound.OutboundMetricsSupport;
import com.aaron.cloud.common.outbound.OutboundRetrySupport;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容流式上游：可配置超时、行间空闲、零输出重试；异常尽量抛 {@link LlmOutboundException} 供主备切换与 SSE 结构化错误。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiUpstreamStreamService {

    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;
    private final ObjectMapper objectMapper;
    private final OutboundMetricsSupport outboundMetricsSupport;

    private final ConcurrentHashMap<Integer, HttpClient> httpClientsByConnectSec = new ConcurrentHashMap<>();

    public void streamChat(
            String baseUrl,
            String apiKey,
            String jsonBody,
            Consumer<String> onContent,
            Consumer<String> onReasoning,
            Consumer<JsonNode> onUsageJson,
            OpenAiCallContext ctx,
            String modelAliasForLog,
            AtomicBoolean cancelRequested)
            throws Exception {
        streamChat(baseUrl, apiKey, jsonBody, onContent, onReasoning, onUsageJson, ctx, modelAliasForLog, cancelRequested, null);
    }

    public void streamChat(
            String baseUrl,
            String apiKey,
            String jsonBody,
            Consumer<String> onContent,
            Consumer<String> onReasoning,
            Consumer<JsonNode> onUsageJson,
            OpenAiCallContext ctx,
            String modelAliasForLog,
            AtomicBoolean cancelRequested,
            OpenAiStreamCapture capture)
            throws Exception {
        long tid = ctx != null && ctx.tenantId() != null ? ctx.tenantId() : 0L;
        AiOutboundResilienceProperties props = outboundResilienceRuntime.effective(tid);
        int maxRetries = Math.max(0, props.getStreamMaxAttempts());
        Exception last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            AtomicInteger emitted = new AtomicInteger(0);
            Consumer<String> wrapC =
                    t -> {
                        if (t != null && !t.isEmpty()) {
                            emitted.incrementAndGet();
                        }
                        onContent.accept(t);
                    };
            Consumer<String> wrapR =
                    r -> {
                        if (r != null && !r.isEmpty()) {
                            emitted.incrementAndGet();
                        }
                        if (onReasoning != null) {
                            onReasoning.accept(r);
                        }
                    };
            try {
                streamOnce(
                        baseUrl,
                        apiKey,
                        jsonBody,
                        wrapC,
                        wrapR,
                        onUsageJson,
                        ctx,
                        modelAliasForLog,
                        props,
                        cancelRequested,
                        capture);
                return;
            } catch (Exception e) {
                last = e;
                boolean zero = emitted.get() == 0;
                boolean canRetry =
                        zero
                                && attempt < maxRetries
                                && isRetryableStreamFailure(props, e);
                if (canRetry) {
                    outboundMetricsSupport.recordRetry(OutboundKind.LLM_STREAM);
                    log.warn(
                            "openai stream retry attempt={}/{} ctx={} cause={}",
                            attempt + 1,
                            maxRetries,
                            ctx == null ? "" : ctx.toLogString(),
                            e.toString());
                    OutboundCircuitBreakerSupport.sleepStreamBackoff(props, attempt);
                } else {
                    throw e;
                }
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private void streamOnce(
            String baseUrl,
            String apiKey,
            String jsonBody,
            Consumer<String> onContent,
            Consumer<String> onReasoning,
            Consumer<JsonNode> onUsageJson,
            OpenAiCallContext ctx,
            String modelAliasForLog,
            AiOutboundResilienceProperties props,
            AtomicBoolean cancelRequested,
            OpenAiStreamCapture capture)
            throws Exception {
        String url = OpenAiChatStreamClient.resolveChatCompletionsUrl(baseUrl);
        String ctxStr = ctx == null ? "(none)" : ctx.toLogString();
        String bodySummary = OpenAiChatStreamClient.summarizeChatJsonBody(objectMapper, jsonBody);
        HttpRequest httpReq =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(props.getStreamRequestTimeoutSeconds()))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
        HttpResponse<InputStream> resp;
        try {
            resp = httpForConnect(props.getConnectTimeoutSeconds()).send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.io.IOException e) {
            log.error(
                    "OpenAI compatible HTTP send IO failed resolvedUrl={} ctx={} {}",
                    url,
                    ctxStr,
                    bodySummary,
                    e);
            throw LlmOutboundException.wrap(
                    OutboundKind.LLM_STREAM, "IO", "OpenAI HTTP IO url=" + url, modelAliasForLog, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(
                    "OpenAI compatible HTTP send interrupted resolvedUrl={} ctx={} {}",
                    url,
                    ctxStr,
                    bodySummary);
            throw e;
        }
        if (resp.statusCode() / 100 != 2) {
            String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            String oneLine = err.replaceAll("\\s+", " ").trim();
            if (oneLine.length() > 480) {
                oneLine = oneLine.substring(0, 480) + "…";
            }
            log.error(
                    "OpenAI compatible HTTP non-2xx status={} resolvedUrl={} ctx={} {} responseBody={}",
                    resp.statusCode(),
                    url,
                    ctxStr,
                    bodySummary,
                    oneLine);
            Long ra =
                    OutboundRetrySupport.parseRetryAfterSeconds(
                            resp.headers().firstValue("Retry-After").orElse(null));
            throw LlmOutboundException.upstreamHttp(
                    OutboundKind.LLM_STREAM,
                    resp.statusCode(),
                    "OpenAI HTTP " + resp.statusCode() + " url=" + url,
                    oneLine,
                    modelAliasForLog,
                    ra);
        }
        InputStream raw = resp.body();
        long firstLineMs = props.getStreamFirstLineTimeoutSeconds() * 1000L;
        long idleMs = props.getStreamLineIdleTimeoutSeconds() * 1000L;
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "openai-sse-line-watchdog");
                            t.setDaemon(true);
                            return t;
                        });
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            String line;
            JsonNode lastUsage = null;
            boolean firstLine = true;
            ReasoningDeltaNormalizer reasoningNormalizer = new ReasoningDeltaNormalizer();
            ToolCallDeltaAccumulator toolAcc =
                    capture != null ? capture.getToolCallAccumulator() : null;
            while (true) {
                if (cancelRequested != null && cancelRequested.get()) {
                    try {
                        raw.close();
                    } catch (Exception ignored) {
                    }
                    log.info(
                            "openai stream cancelled ctx={} url={}",
                            ctxStr,
                            url);
                    break;
                }
                long budget = firstLine ? firstLineMs : idleMs;
                ScheduledFuture<?> killer =
                        scheduler.schedule(
                                () -> {
                                    try {
                                        raw.close();
                                    } catch (Exception ignored) {
                                    }
                                },
                                budget,
                                TimeUnit.MILLISECONDS);
                try {
                    line = br.readLine();
                } finally {
                    killer.cancel(false);
                }
                if (line == null) {
                    break;
                }
                firstLine = false;
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equalsIgnoreCase(data)) {
                    continue;
                }
                JsonNode root;
                try {
                    root = objectMapper.readTree(data);
                } catch (JsonProcessingException jpe) {
                    String preview = data.length() > 240 ? data.substring(0, 240) + "…" : data;
                    log.error(
                            "OpenAI compatible SSE line JSON parse failed resolvedUrl={} ctx={} {} sseDataPreview={}",
                            url,
                            ctxStr,
                            bodySummary,
                            preview,
                            jpe);
                    throw jpe;
                }
                JsonNode usage = root.get("usage");
                if (usage != null && usage.isObject()) {
                    lastUsage = usage;
                }
                JsonNode choices = root.get("choices");
                if (choices == null || !choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode first = choices.get(0);
                if (first.hasNonNull("finish_reason") && capture != null) {
                    capture.setFinishReason(first.get("finish_reason").asText());
                }
                JsonNode delta = first.get("delta");
                if (delta == null || !delta.isObject()) {
                    continue;
                }
                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                    String r = reasoningNormalizer.toDelta(delta.get("reasoning_content").asText(""));
                    if (!r.isEmpty() && onReasoning != null) {
                        onReasoning.accept(r);
                        if (cancelRequested != null && cancelRequested.get()) {
                            try {
                                raw.close();
                            } catch (Exception ignored) {
                            }
                            break;
                        }
                    }
                }
                if (delta.has("content") && !delta.get("content").isNull()) {
                    String c = delta.get("content").asText("");
                    if (!c.isEmpty()) {
                        if (capture != null) {
                            capture.appendContent(c);
                        }
                        onContent.accept(c);
                    }
                }
                if (toolAcc != null && delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                    toolAcc.accept(delta.get("tool_calls"));
                }
            }
            if (lastUsage != null && onUsageJson != null) {
                onUsageJson.accept(lastUsage);
            }
        } catch (java.io.IOException e) {
            if (cancelRequested != null && cancelRequested.get()) {
                log.info(
                        "openai stream cancelled during read ctx={} url={}",
                        ctxStr,
                        url);
                return;
            }
            throw LlmOutboundException.wrap(
                    OutboundKind.LLM_STREAM,
                    "SSE_IO",
                    "SSE 读取失败 url=" + url,
                    modelAliasForLog,
                    e);
        } finally {
            scheduler.shutdownNow();
        }
        log.debug("openai stream finished url={}", url);
    }

    private HttpClient httpForConnect(int connectTimeoutSeconds) {
        int sec = Math.max(1, Math.min(connectTimeoutSeconds, 600));
        return httpClientsByConnectSec.computeIfAbsent(
                sec, s -> HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(s)).build());
    }

    private static boolean isRetryableStreamFailure(AiOutboundResilienceProperties props, Throwable e) {
        if (e instanceof IllegalArgumentException) {
            return false;
        }
        if (e instanceof LlmOutboundException lo) {
            Integer st = lo.getHttpStatus();
            return st != null && OutboundRetrySupport.isRetryableHttpStatus(props, st);
        }
        return OutboundRetrySupport.isRetryableException(props, e);
    }
}
