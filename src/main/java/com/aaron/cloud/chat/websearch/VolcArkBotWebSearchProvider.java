package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.enums.llm.LlmWebSearchProvider;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.outbound.OutboundCircuitBreakerSupport;
import com.aaron.cloud.common.api.enums.infra.OutboundKind;
import com.aaron.cloud.common.outbound.OutboundMetricsSupport;
import com.aaron.cloud.common.outbound.OutboundTenantUpstreamQuarantine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 火山引擎 Ark：Bot 非流式 Chat Completions（须 Bot 已开通联网能力）；请求/解析不依赖 volcengine SDK，避免版本锁定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VolcArkBotWebSearchProvider implements WebSearchModelProvider {

    /** 引用为空时 INFO 打出原始 JSON 的上限（便于对照 Ark 实际字段）。 */
    private static final int RAW_RESPONSE_LOG_MAX_CHARS = 32_768;

    private final ObjectMapper objectMapper;
    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;
    private final OutboundCircuitBreakerSupport outboundCircuitBreakerSupport;
    private final OutboundMetricsSupport outboundMetricsSupport;
    private final OutboundTenantUpstreamQuarantine outboundTenantUpstreamQuarantine;

    @Override
    public LlmWebSearchProvider supports() {
        return LlmWebSearchProvider.VOLCENGINE_ARK_BOT;
    }

    @Override
    public WebSearchExecutionResult execute(
            SysLlmModel row, String apiKeyPlaintext, WebSearchArkInvokeRequest request) throws Exception {
        long tid = row.getTenantId() == null ? 0L : row.getTenantId();
        outboundTenantUpstreamQuarantine.assertStreamAllowed(tid);
        if (!outboundResilienceRuntime.effective(tid).isEnabled()) {
            try {
                WebSearchExecutionResult r = doExecute(row, apiKeyPlaintext, request);
                outboundTenantUpstreamQuarantine.recordTenantSuccess(tid);
                return r;
            } catch (Exception e) {
                outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tid, e);
                throw e;
            }
        }
        var cb = outboundCircuitBreakerSupport.forWebSearch(row.getTenantId(), row.getId());
        long t0 = System.nanoTime();
        if (cb.isPresent() && !cb.get().tryAcquirePermission()) {
            outboundMetricsSupport.recordError(OutboundKind.LLM_WEB_SEARCH, "CIRCUIT_OPEN", 503);
            throw OutboundCircuitBreakerSupport.circuitOpen(OutboundKind.LLM_WEB_SEARCH, row.getAlias());
        }
        try {
            WebSearchExecutionResult r = doExecute(row, apiKeyPlaintext, request);
            cb.ifPresent(c -> c.onSuccess(System.nanoTime() - t0, TimeUnit.NANOSECONDS));
            outboundTenantUpstreamQuarantine.recordTenantSuccess(tid);
            return r;
        } catch (Exception e) {
            cb.ifPresent(c -> c.onError(System.nanoTime() - t0, TimeUnit.NANOSECONDS, e));
            if (!(e instanceof IllegalArgumentException)) {
                outboundMetricsSupport.recordError(
                        OutboundKind.LLM_WEB_SEARCH, e.getClass().getSimpleName(), null);
            }
            outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tid, e);
            throw e;
        }
    }

    private WebSearchExecutionResult doExecute(
            SysLlmModel row, String apiKeyPlaintext, WebSearchArkInvokeRequest request) throws Exception {
        String url = ArkBotChatCompletionsUrl.normalize(row.getOpenaiBaseUrl());
        if (url.isBlank()) {
            throw new IllegalArgumentException("联网搜索模型未配置有效的 Ark Base URL");
        }
        String botId = row.getOpenaiModelId() == null ? "" : row.getOpenaiModelId().trim();
        if (botId.isBlank()) {
            throw new IllegalArgumentException("联网搜索模型未配置 Bot ID（openai_model_id）");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", botId);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        List<ModelChatRequest.MessageTurn> turns =
                request == null || request.messages() == null ? List.of() : request.messages();
        if (turns.isEmpty()) {
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", "");
        } else {
            for (ModelChatRequest.MessageTurn turn : turns) {
                if (turn == null || turn.getRole() == null) {
                    continue;
                }
                ObjectNode node = messages.addObject();
                node.put("role", turn.getRole().trim().toLowerCase());
                node.put("content", turn.getContent() == null ? "" : turn.getContent());
            }
        }

        RestClient client = RestClient.builder().build();
        String respBody =
                client.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + apiKeyPlaintext)
                        .body(objectMapper.writeValueAsString(body))
                        .retrieve()
                        .body(String.class);
        return parseResponseBody(respBody);
    }

    private WebSearchExecutionResult parseResponseBody(String respBody) throws Exception {
        if (respBody == null || respBody.isBlank()) {
            throw new IllegalStateException("Ark Bot 返回空响应");
        }
        JsonNode root = objectMapper.readTree(respBody.getBytes(StandardCharsets.UTF_8));
        JsonNode err = root.path("error");
        if (!err.isMissingNode() && err.isObject()) {
            String msg = err.path("message").asText("Ark 错误");
            throw new IllegalStateException(msg);
        }
        String summary = VolcArkBotReferenceParser.extractAssistantText(root);
        List<WebSearchReference> refs = VolcArkBotReferenceParser.mergeReferences(objectMapper, root);
        logArkOutcome(root, respBody, summary, refs);
        return new WebSearchExecutionResult(new WebGroundingBundle(summary, refs), respBody);
    }

    private void logArkOutcome(JsonNode root, String respBody, String summary, List<WebSearchReference> refs) {
        int refCount = refs == null ? 0 : refs.size();
        if (refCount > 0) {
            log.debug(
                    "[联网搜索] Ark 解析完成：引用 {} 条，摘要 {} 字",
                    refCount,
                    summary == null ? 0 : summary.length());
            return;
        }
        if (summary == null || summary.isBlank()) {
            log.info("[联网搜索] Ark 原始响应（摘要与引用均为空）：{}", clipForLog(respBody));
            return;
        }
        log.warn(
                "[联网搜索] Ark 有摘要无结构化引用：{}",
                VolcArkBotReferenceParser.summarizeReferenceDiagnostics(objectMapper, root));
        log.info("[联网搜索] Ark 原始响应（引用为空，全文）：{}", clipForLog(respBody));
    }

    private static String clipForLog(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\r', ' ').trim();
        if (oneLine.length() <= RAW_RESPONSE_LOG_MAX_CHARS) {
            return oneLine;
        }
        return oneLine.substring(0, RAW_RESPONSE_LOG_MAX_CHARS)
                + "…(截断，原长 "
                + oneLine.length()
                + " 字)";
    }
}
