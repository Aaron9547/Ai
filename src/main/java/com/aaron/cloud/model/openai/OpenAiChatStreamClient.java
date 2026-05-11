package com.aaron.cloud.model.openai;

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
import java.util.Locale;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class OpenAiChatStreamClient {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private OpenAiChatStreamClient() {}

    /**
     * 将管理端配置的 {@code openai_base_url} 规范为可发起流式补全的完整 URL（末尾为 {@code …/chat/completions}）。
     *
     * <p>不在配置根后<strong>自动插入</strong> {@code /v1}，避免方舟等已含 {@code …/api/v3} 时再拼出 {@code …/api/v3/v1/chat/completions} 导致
     * 404。规则：已以 {@code /chat/completions} 结尾则原样；已以 {@code /v1} 结尾则只补 {@code /chat/completions}；否则仅补
     * {@code /chat/completions}。OpenAI 官方请将 Base 配到 {@code https://api.openai.com/v1}，火山方舟配到 {@code …/api/v3} 等。
     */
    public static String resolveChatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("openai_base_url 为空");
        }
        String u = baseUrl.trim().replaceAll("/+$", "");
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/chat/completions")) {
            return u;
        }
        if (lower.endsWith("/v1")) {
            return u + "/chat/completions";
        }
        return u + "/chat/completions";
    }

    /**
     * 请求体摘要：含上游 {@code model}、消息条数、角色序列、JSON 长度；**不**输出各条 message 的 content，避免日志泄露对话原文。
     */
    static String summarizeChatJsonBody(ObjectMapper om, String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return "upstreamJsonLen=0";
        }
        try {
            JsonNode root = om.readTree(jsonBody);
            String model = root.path("model").asText("?");
            boolean stream = root.path("stream").asBoolean(false);
            JsonNode msgs = root.get("messages");
            int n = msgs != null && msgs.isArray() ? msgs.size() : 0;
            StringBuilder roles = new StringBuilder();
            if (msgs != null && msgs.isArray()) {
                int cap = Math.min(msgs.size(), 12);
                for (int i = 0; i < cap; i++) {
                    if (i > 0) {
                        roles.append(',');
                    }
                    roles.append(msgs.get(i).path("role").asText("?"));
                }
                if (msgs.size() > cap) {
                    roles.append(",…");
                }
            }
            return "upstreamBody model="
                    + model
                    + " stream="
                    + stream
                    + " msgCount="
                    + n
                    + " roles=["
                    + roles
                    + "] jsonLen="
                    + jsonBody.length();
        } catch (Exception e) {
            return "upstreamBody parseFailed jsonLen=" + jsonBody.length();
        }
    }

    /**
     * @param onUsageJson 可选；当响应含根级 {@code usage}（如豆包/OpenAI 在 {@code stream_options.include_usage}
     *     下最后一帧）时回调
     */
    public static void streamChat(
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String jsonBody,
            Consumer<String> onContent,
            Consumer<String> onReasoning,
            Consumer<JsonNode> onUsageJson)
            throws Exception {
        streamChat(objectMapper, baseUrl, apiKey, jsonBody, onContent, onReasoning, onUsageJson, null);
    }

    public static void streamChat(
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String jsonBody,
            Consumer<String> onContent,
            Consumer<String> onReasoning,
            Consumer<JsonNode> onUsageJson,
            OpenAiCallContext ctx)
            throws Exception {
        String url = resolveChatCompletionsUrl(baseUrl);
        String ctxStr = ctx == null ? "(none)" : ctx.toLogString();
        String bodySummary = summarizeChatJsonBody(objectMapper, jsonBody);
        HttpRequest httpReq =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(5))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
        HttpResponse<InputStream> resp;
        try {
            resp = HTTP.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.io.IOException e) {
            log.error(
                    "OpenAI compatible HTTP send IO failed resolvedUrl={} ctx={} {}",
                    url,
                    ctxStr,
                    bodySummary,
                    e);
            throw new IllegalStateException(
                    "OpenAI HTTP IO url=" + url + " cause=" + e.getMessage(), e);
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
            throw new IllegalStateException("OpenAI HTTP " + resp.statusCode() + " url=" + url + " body=" + oneLine);
        }
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            JsonNode lastUsage = null;
            while ((line = br.readLine()) != null) {
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
                JsonNode delta = first.get("delta");
                if (delta == null || !delta.isObject()) {
                    continue;
                }
                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                    String r = delta.get("reasoning_content").asText("");
                    if (!r.isEmpty() && onReasoning != null) {
                        onReasoning.accept(r);
                    }
                }
                if (delta.has("content") && !delta.get("content").isNull()) {
                    String c = delta.get("content").asText("");
                    if (!c.isEmpty()) {
                        onContent.accept(c);
                    }
                }
            }
            if (lastUsage != null && onUsageJson != null) {
                onUsageJson.accept(lastUsage);
            }
        }
        log.debug("openai stream finished url={}", url);
    }
}
