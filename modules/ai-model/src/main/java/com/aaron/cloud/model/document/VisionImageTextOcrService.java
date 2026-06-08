package com.aaron.cloud.model.document;

import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
import com.aaron.cloud.common.document.VisionModelHints;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.model.openai.OpenAiChatStreamClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 租户视觉模型图片 OCR（OpenAI 兼容 {@code image_url}）；由 {@link ChainedImageTextOcrPort} 在本机 OCR 之后调用。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionImageTextOcrService {

    private static final String OCR_USER_PROMPT =
            "请识别图片中的全部可见文字，按从上到下、从左到右的阅读顺序逐行输出。"
                    + "重点保留：标题、项目名称、楼盘/户型编号、房间名称（客厅、卧室、厨房、阳台、卫、玄关等）、"
                    + "图中中文标注与英文型号。"
                    + "纯尺寸数字、表格边框线可省略；不要输出 Markdown 表格、不要解释、不要总结。"
                    + "若无文字则只回复：无文字";

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private final SysLlmModelRepository llmModelRepository;
    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;

    public Optional<String> tryExtract(long tenantId, byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        Optional<SysLlmModel> model = pickVisionModel(tenantId);
        if (model.isEmpty()) {
            log.info(
                    "[文档图片OCR] 租户 {} 无可用视觉模型（请配置 VISION 类型或带 vision/vl 标识的语言模型）",
                    tenantId);
            return Optional.empty();
        }
        SysLlmModel m = model.get();
        String cipher = m.getApiKeyCipher();
        if (cipher == null || cipher.isBlank()) {
            log.warn("[文档图片OCR] 视觉模型 {} 未配置 API Key", m.getAlias());
            return Optional.empty();
        }
        try {
            String apiKey = aesSecretCipher.decryptFromBase64(cipher);
            String url = OpenAiChatStreamClient.resolveChatCompletionsUrl(m.getOpenaiBaseUrl());
            String safeMime =
                    mimeType == null || mimeType.isBlank() || !mimeType.startsWith("image/")
                            ? "image/png"
                            : mimeType;
            String dataUrl =
                    "data:" + safeMime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            String body = buildVisionRequestJson(m.getOpenaiModelId(), dataUrl);
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(90))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            HttpResponse<String> resp =
                    HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn(
                        "[文档图片OCR] 上游 HTTP {}：模型 alias={}，body={}",
                        resp.statusCode(),
                        m.getAlias(),
                        truncateForLog(resp.body()));
                return Optional.empty();
            }
            String text = parseAssistantContent(resp.body());
            if (text == null || text.isBlank() || "无文字".equals(text.trim())) {
                return Optional.empty();
            }
            log.info("[文档图片OCR] 成功：租户 {}，模型 {}，字数 {}", tenantId, m.getAlias(), text.length());
            return Optional.of(text.trim());
        } catch (Exception ex) {
            log.warn("[文档图片OCR] 失败：租户 {}，模型 {}", tenantId, m.getAlias(), ex);
            return Optional.empty();
        }
    }

    private Optional<SysLlmModel> pickVisionModel(long tenantId) {
        Optional<SysLlmModel> dedicated = llmModelRepository.pickDefaultVisionModel(tenantId);
        if (dedicated.isPresent()) {
            return dedicated;
        }
        List<SysLlmModel> catalog = llmModelRepository.listForCatalog(tenantId, false);
        Optional<SysLlmModel> fromCatalog =
                catalog.stream()
                        .filter(m -> m.getStatus() == LlmModelStatus.ACTIVE)
                        .filter(VisionModelHints::looksVisionCapable)
                        .findFirst();
        if (fromCatalog.isPresent()) {
            return fromCatalog;
        }
        return llmModelRepository
                .pickDefaultLanguageModel(tenantId)
                .filter(VisionModelHints::looksVisionCapable);
    }

    private static String truncateForLog(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String s = body.replace('\n', ' ').trim();
        return s.length() <= 240 ? s : s.substring(0, 240) + "…";
    }

    private String buildVisionRequestJson(String openaiModelId, String dataUrl) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", openaiModelId);
        body.put("stream", false);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", OCR_USER_PROMPT);
        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        imagePart.putObject("image_url").put("url", dataUrl);
        return objectMapper.writeValueAsString(body);
    }

    private String parseAssistantContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText()) && part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
            return sb.toString();
        }
        return null;
    }
}
