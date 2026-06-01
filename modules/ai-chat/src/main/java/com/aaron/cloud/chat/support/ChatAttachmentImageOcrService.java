package com.aaron.cloud.chat.support;

import com.aaron.cloud.common.api.enums.llm.LlmModelStatus;
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

/**
 * 图片附件在 Tika 无文本时，尝试用租户已配置的<strong>视觉对话模型</strong>做 OCR 式抽取（OpenAI 兼容 {@code image_url}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentImageOcrService {

    private static final String OCR_USER_PROMPT =
            "请仅输出图片中的全部可见文字（保持段落与换行），不要解释、不要 Markdown 代码块；若无文字则只回复：无文字";

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
            log.info("[对话附件] 租户 {} 无可用视觉模型（请在管理端配置 VISION 类型或带 vision/vl 标识的语言模型），跳过图片 OCR", tenantId);
            return Optional.empty();
        }
        SysLlmModel m = model.get();
        String cipher = m.getApiKeyCipher();
        if (cipher == null || cipher.isBlank()) {
            log.warn("[对话附件] 视觉模型 {} 未配置 API Key，跳过图片 OCR", m.getAlias());
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
                        "[对话附件] 图片 OCR 上游 HTTP {}：模型 alias={}，body={}",
                        resp.statusCode(),
                        m.getAlias(),
                        truncateForLog(resp.body()));
                return Optional.empty();
            }
            String text = parseAssistantContent(resp.body());
            if (text == null || text.isBlank() || "无文字".equals(text.trim())) {
                return Optional.empty();
            }
            log.info(
                    "[对话附件] 图片 OCR 成功：租户 {}，模型 {}，字数 {}",
                    tenantId,
                    m.getAlias(),
                    text.length());
            return Optional.of(text.trim());
        } catch (Exception ex) {
            log.warn("[对话附件] 图片 OCR 失败：租户 {}，模型 {}", tenantId, m.getAlias(), ex);
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
                        .filter(ChatAttachmentVisionModelHints::looksVisionCapable)
                        .findFirst();
        if (fromCatalog.isPresent()) {
            return fromCatalog;
        }
        return llmModelRepository
                .pickDefaultLanguageModel(tenantId)
                .filter(ChatAttachmentVisionModelHints::looksVisionCapable);
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
