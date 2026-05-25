package com.aaron.cloud.chat.websearch;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 火山引擎 Ark：Bot 非流式 Chat Completions（须 Bot 已开通联网能力）；请求/解析不依赖 volcengine SDK，避免版本锁定。
 */
@Component
@RequiredArgsConstructor
public class VolcArkBotWebSearchProvider implements WebSearchModelProvider {

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
    public WebSearchExecutionResult execute(SysLlmModel row, String apiKeyPlaintext, String userQueryPlaintext)
            throws Exception {
        long tid = row.getTenantId() == null ? 0L : row.getTenantId();
        outboundTenantUpstreamQuarantine.assertStreamAllowed(tid);
        if (!outboundResilienceRuntime.effective(tid).isEnabled()) {
            try {
                WebSearchExecutionResult r = doExecute(row, apiKeyPlaintext, userQueryPlaintext);
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
            WebSearchExecutionResult r = doExecute(row, apiKeyPlaintext, userQueryPlaintext);
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

    private WebSearchExecutionResult doExecute(SysLlmModel row, String apiKeyPlaintext, String userQueryPlaintext)
            throws Exception {
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
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userQueryPlaintext == null ? "" : userQueryPlaintext);

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
        if (respBody == null || respBody.isBlank()) {
            throw new IllegalStateException("Ark Bot 返回空响应");
        }
        JsonNode root = objectMapper.readTree(respBody.getBytes(StandardCharsets.UTF_8));
        JsonNode err = root.path("error");
        if (!err.isMissingNode() && err.isObject()) {
            String msg = err.path("message").asText("Ark 错误");
            throw new IllegalStateException(msg);
        }
        String summary =
                root.path("choices").path(0).path("message").path("content").asText("").trim();
        List<WebSearchReference> refs = mergeReferences(root);
        return new WebSearchExecutionResult(new WebGroundingBundle(summary, refs), respBody);
    }

    /** 根级 {@code references} 优先，再合并 {@code bot_usage} 内检索 {@code results}（按 URL 去重）。 */
    private List<WebSearchReference> mergeReferences(JsonNode root) {
        Map<String, WebSearchReference> byKey = new LinkedHashMap<>();
        if (root.path("references").isArray()) {
            for (JsonNode n : root.path("references")) {
                WebSearchReference r = mapReferenceNode(n);
                putDedupe(byKey, r);
            }
        }
        for (WebSearchReference r : parseBotUsageReferences(root)) {
            putDedupe(byKey, r);
        }
        return List.copyOf(byKey.values());
    }

    private void putDedupe(Map<String, WebSearchReference> byKey, WebSearchReference r) {
        String k = dedupeKey(r);
        if (!k.isBlank()) {
            byKey.putIfAbsent(k, r);
        }
    }

    private static String dedupeKey(WebSearchReference r) {
        if (r.url() != null && !r.url().isBlank()) {
            return r.url().trim();
        }
        if (r.title() != null && !r.title().isBlank()) {
            return "t:" + r.title().trim();
        }
        return "";
    }

    private WebSearchReference mapReferenceNode(JsonNode r) {
        if (r == null || !r.isObject()) {
            return WebSearchReference.ofTitleUrlSnippet("", "", "");
        }
        String title = textOrEmpty(r, "title");
        String u = textOrEmpty(r, "url");
        String snippet = firstNonBlank(textOrEmpty(r, "summary"), textOrEmpty(r, "content"));
        String siteName = firstNonBlank(textOrEmpty(r, "site_name"), textOrEmpty(r, "siteName"));
        String logoUrl = firstNonBlank(textOrEmpty(r, "logo_url"), textOrEmpty(r, "logoUrl"));
        String publishTime = formatPublishTime(r.get("publish_time"));
        String extraJson = buildExtraJson(r.path("extra"), null);
        return new WebSearchReference(
                nz(title),
                nz(u),
                nz(snippet),
                blankToNull(siteName),
                blankToNull(logoUrl),
                blankToNull(publishTime),
                extraJson);
    }

    private List<WebSearchReference> parseBotUsageReferences(JsonNode root) {
        List<WebSearchReference> list = new ArrayList<>();
        JsonNode details = root.path("bot_usage").path("action_details");
        if (!details.isArray()) {
            return list;
        }
        for (JsonNode ad : details) {
            JsonNode tools = ad.path("tool_details");
            if (!tools.isArray()) {
                continue;
            }
            for (JsonNode td : tools) {
                JsonNode results = td.path("output").path("data").path("data").path("results");
                if (!results.isArray()) {
                    continue;
                }
                for (JsonNode item : results) {
                    list.add(mapSearchResultItem(item));
                }
            }
        }
        return list;
    }

    private WebSearchReference mapSearchResultItem(JsonNode r) {
        if (r == null || !r.isObject()) {
            return WebSearchReference.ofTitleUrlSnippet("", "", "");
        }
        String title = textOrEmpty(r, "title");
        String u = textOrEmpty(r, "url");
        String snippet = firstNonBlank(textOrEmpty(r, "summary"), textOrEmpty(r, "content"));
        String siteName = firstNonBlank(textOrEmpty(r, "site_name"), textOrEmpty(r, "siteName"));
        JsonNode spd = r.path("search_plugin_data");
        String logoUrl = firstNonBlank(textOrEmpty(r, "logo_url"), textOrEmpty(r, "logoUrl"));
        if ((logoUrl == null || logoUrl.isBlank()) && spd.isObject()) {
            logoUrl = textOrEmpty(spd, "logo_url");
        }
        String publishTime = formatPublishTime(r.get("publish_time"));
        String extraJson = buildExtraJson(r.path("extra"), spd.isObject() ? spd : null);
        return new WebSearchReference(
                nz(title),
                nz(u),
                nz(snippet),
                blankToNull(siteName),
                blankToNull(logoUrl),
                blankToNull(publishTime),
                extraJson);
    }

    private String buildExtraJson(JsonNode extra, JsonNode pluginData) {
        boolean hasE = extra != null && !extra.isMissingNode() && !extra.isNull();
        boolean hasP = pluginData != null && !pluginData.isMissingNode() && !pluginData.isNull();
        if (!hasE && !hasP) {
            return null;
        }
        try {
            if (hasE && !hasP) {
                return objectMapper.writeValueAsString(extra);
            }
            if (!hasE) {
                return objectMapper.writeValueAsString(pluginData);
            }
            ObjectNode wrap = objectMapper.createObjectNode();
            wrap.set("extra", extra);
            wrap.set("search_plugin_data", pluginData);
            return objectMapper.writeValueAsString(wrap);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatPublishTime(JsonNode pt) {
        if (pt == null || pt.isNull() || pt.isMissingNode()) {
            return null;
        }
        if (pt.isIntegralNumber()) {
            long epoch = pt.asLong();
            if (epoch <= 0) {
                return null;
            }
            return Long.toString(epoch);
        }
        if (pt.isTextual()) {
            String t = pt.asText().trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

    private static String textOrEmpty(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() ? v.asText().trim() : "";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b == null ? "" : b;
    }
}
