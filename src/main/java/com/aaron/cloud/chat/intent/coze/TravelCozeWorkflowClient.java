package com.aaron.cloud.chat.intent.coze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 调用 Coze OpenAPI：{@code POST /v1/files/upload}、{@code POST /v1/workflow/stream_run}。
 * 与 ly-ai-application {@code TravelReimbursementService} 语义对齐（工作流为同步聚合，在虚拟线程中调用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TravelCozeWorkflowClient {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private final ObjectMapper objectMapper;

    /**
     * {@code POST /v1/files/upload}，multipart 字段名 {@code file}，与 ly
     * {@code ModelHttpRequestUtils.executeUploadCall(..., "file", ...)} 一致。
     *
     * @return Coze 侧文件 id，供工作流 parameters 中 {@code file}（JSON 字符串 {@code {"file_id":"..."}}）
     */
    public String uploadFile(String domain, String apiKey, String fileName, byte[] content) throws Exception {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("empty file content");
        }
        String base = normalizeDomain(domain);
        String url = base + "/v1/files/upload";
        String safeName = sanitizeMultipartFilename(fileName);
        String boundary = "----AiCozeFileBoundary" + UUID.randomUUID();
        byte[] multipartBody = buildMultipartFileBody(boundary, safeName, content);

        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(3))
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                        .build();

        log.info("[意图链路][Coze] files/upload 请求 url={} fileName={} bytes={}", url, safeName, content.length);
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String raw = resp.body() == null ? "" : resp.body();
        if (code < 200 || code >= 300) {
            String preview = raw.length() > 512 ? raw.substring(0, 512) : raw;
            log.warn("[意图链路][Coze] files/upload HTTP {} bodyPreview={}", code, preview);
            throw new IllegalStateException("Coze files/upload HTTP " + code);
        }
        String fileId = parseCozeUploadFileId(raw);
        log.info("[意图链路][Coze] files/upload 完成 cozeFileId={}", fileId);
        return fileId;
    }

    public String collectWorkflowOutput(String domain, String apiKey, String workflowId, Map<String, Object> parameters)
            throws Exception {
        String base = normalizeDomain(domain);
        String url = base + "/v1/workflow/stream_run";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("workflow_id", workflowId);
        body.set("parameters", objectMapper.valueToTree(parameters));

        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(10))
                        .header("Accept", "text/event-stream, application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();

        log.info("[意图链路][Coze] stream_run 请求 workflowId={} url={}", workflowId, url);
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            String errBody = readAllLimited(resp.body(), 4096);
            log.warn("[意图链路][Coze] stream_run HTTP {} bodyPreview={}", code, errBody);
            throw new IllegalStateException("Coze stream_run HTTP " + code);
        }

        StringBuilder raw = new StringBuilder(8192);
        try (BufferedReader br =
                new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                raw.append(line).append('\n');
                if (line.startsWith("data:")) {
                    String payload = line.replaceFirst("(?i)^data:\\s*", "").trim();
                    JsonNode end = TravelCozeResponseParser.tryParseEndNode(objectMapper, payload);
                    if (end != null && "End".equalsIgnoreCase(end.path("node_type").asText(""))) {
                        break;
                    }
                }
            }
        }
        String out = TravelCozeResponseParser.extractWorkflowOutput(objectMapper, raw.toString());
        log.info("[意图链路][Coze] stream_run 完成 workflowId={} outputLen={}", workflowId, out == null ? 0 : out.length());
        return out == null ? "" : out;
    }

    private static String readAllLimited(InputStream in, int max) throws Exception {
        if (in == null) {
            return "";
        }
        byte[] buf = in.readNBytes(max);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static String normalizeDomain(String domain) {
        String d = domain == null ? "" : domain.trim();
        if (d.isEmpty()) {
            return "https://api.coze.cn";
        }
        if (d.endsWith("/")) {
            return d.substring(0, d.length() - 1);
        }
        return d;
    }

    private static byte[] buildMultipartFileBody(String boundary, String fileName, byte[] fileBytes) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(fileBytes.length + 256);
        String head =
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                        + fileName
                        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n";
        baos.write(head.getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    private static String sanitizeMultipartFilename(String name) {
        String n = (name == null || name.isBlank()) ? "upload.bin" : name.trim();
        n = n.replace("\"", "_").replace("\r", "_").replace("\n", "_");
        if (n.length() > 200) {
            n = n.substring(n.length() - 200);
        }
        return n;
    }

    private String parseCozeUploadFileId(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Coze upload invalid JSON: " + raw);
        }
        String id = firstNonBlankJsonText(root, "file_id", "id");
        JsonNode data = root.get("data");
        if ((id == null || id.isBlank()) && data != null && data.isObject()) {
            id = firstNonBlankJsonText(data, "file_id", "id");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Coze upload response missing file id: " + raw);
        }
        return id;
    }

    private static String firstNonBlankJsonText(JsonNode n, String... fields) {
        for (String f : fields) {
            if (n == null || !n.has(f) || n.get(f).isNull()) {
                continue;
            }
            JsonNode v = n.get(f);
            String t = v.isTextual() ? v.asText() : v.toString();
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return null;
    }
}
