package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.model.openai.OpenAiCallContext;
import com.aaron.cloud.model.openai.OpenAiChatStreamClient;
import com.aaron.cloud.model.openai.OpenAiUsageParser;
import com.aaron.cloud.model.spi.ModelCompletionEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统一模型推理：{@code mock} 走本地占位；否则必须命中租户 {@code llm_model}（OpenAI 兼容 SSE），不再使用 yaml
 * 全局 OpenAI 回退。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultModelCompletionEngine implements ModelCompletionEngine {

    private final SysLlmModelRepository llmModelRepository;
    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;

    @Override
    public void streamCompletion(ModelChatRequest request, Consumer<String> onToken) throws Exception {
        String alias =
                request.getModelAlias() == null || request.getModelAlias().isBlank()
                        ? "mock"
                        : request.getModelAlias().trim();
        if ("mock".equalsIgnoreCase(alias)) {
            runMock(request, onToken);
            return;
        }
        Long tenantId = request.getTenantId();
        if (tenantId == null) {
            var snap = TenantContextHolder.getOrNull();
            if (snap != null) {
                tenantId = snap.getTenantId();
            }
        }
        if (tenantId == null) {
            throw new IllegalStateException("缺少 tenantId，无法解析模型配置");
        }
        var cfgOpt = llmModelRepository.findByTenantAndAlias(tenantId, alias);
        if (cfgOpt.isPresent()) {
            SysLlmModel m = cfgOpt.get();
            LlmModelKindPolicy.assertLanguageModelForChatStream(m);
            if (m.getApiKeyCipher() == null || m.getApiKeyCipher().isBlank()) {
                throw new IllegalStateException("模型未配置 API Key：" + alias);
            }
            String apiKey = aesSecretCipher.decryptFromBase64(m.getApiKeyCipher());
            String json = buildOpenAiJsonBody(request, m.getOpenaiModelId(), true);
            Consumer<String> reasoning =
                    Boolean.TRUE.equals(request.getThinkingEnabled())
                                    && request.getReasoningTokenConsumer() != null
                            ? request.getReasoningTokenConsumer()
                            : null;
            Consumer<JsonNode> onUsage = usageCallback(request);
            OpenAiChatStreamClient.streamChat(
                    objectMapper,
                    m.getOpenaiBaseUrl(),
                    apiKey,
                    json,
                    onToken,
                    reasoning,
                    onUsage,
                    new OpenAiCallContext(
                            tenantId, alias, m.getId(), m.getOpenaiModelId(), m.getOpenaiBaseUrl()));
            return;
        }
        throw new IllegalArgumentException(
                "未找到该租户下的大模型配置（模型别名："
                        + alias
                        + "）。请先在管理端为租户配置并启用 llm_model 后再发起对话。");
    }

    private void runMock(ModelChatRequest request, Consumer<String> onToken) throws InterruptedException {
        List<ModelChatRequest.MessageTurn> turns = request.getMessages();
        String lastUser =
                turns == null || turns.isEmpty()
                        ? ""
                        : turns.stream()
                                .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                                .reduce((a, b) -> b)
                                .map(ModelChatRequest.MessageTurn::getContent)
                                .orElse("");
        String reply = "（mock）已收到：" + lastUser;
        for (String part : reply.split("")) {
            if (!part.isEmpty()) {
                onToken.accept(part);
                Thread.sleep(3);
            }
        }
    }

    private String buildOpenAiJsonBody(ModelChatRequest request, String openaiModelId, boolean stream)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", openaiModelId);
        body.put("stream", stream);
        if (stream) {
            body.putObject("stream_options").put("include_usage", true);
        }
        ArrayNode messages = body.putArray("messages");
        List<ModelChatRequest.MessageTurn> turns = request.getMessages();
        if (turns != null) {
            for (ModelChatRequest.MessageTurn t : turns) {
                ObjectNode m = messages.addObject();
                m.put("role", t.getRole());
                m.put("content", t.getContent() == null ? "" : t.getContent());
            }
        }
        return objectMapper.writeValueAsString(body);
    }

    private static Consumer<JsonNode> usageCallback(ModelChatRequest request) {
        if (request.getStreamUsageConsumer() == null) {
            return null;
        }
        return node -> request.getStreamUsageConsumer().accept(OpenAiUsageParser.parse(node));
    }
}
