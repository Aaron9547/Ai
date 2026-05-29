package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelStreamResult;
import com.aaron.cloud.common.api.dto.model.ModelToolCall;
import com.aaron.cloud.common.api.dto.model.ModelToolDefinition;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.modelcfg.LlmModelKindPolicy;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;
import com.aaron.cloud.common.outbound.LlmOutboundException;
import com.aaron.cloud.common.outbound.OutboundRetrySupport;
import com.aaron.cloud.common.outbound.OutboundTenantUpstreamQuarantine;
import com.aaron.cloud.common.outbound.TenantOutboundResilienceRuntime;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.model.openai.OpenAiCallContext;
import com.aaron.cloud.model.openai.OpenAiStreamCapture;
import com.aaron.cloud.model.openai.OpenAiUpstreamStreamService;
import com.aaron.cloud.model.openai.OpenAiUsageParser;
import com.aaron.cloud.model.spi.ModelCompletionEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统一模型推理：{@code mock} 走本地占位；否则必须命中租户 {@code llm_model}（OpenAI 兼容 SSE），不再使用 yaml
 * 全局 OpenAI 回退。支持 {@code fallback_model_alias} 主备链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultModelCompletionEngine implements ModelCompletionEngine {

    private final SysLlmModelRepository llmModelRepository;
    private final AesSecretCipher aesSecretCipher;
    private final ObjectMapper objectMapper;
    private final OpenAiUpstreamStreamService openAiUpstreamStreamService;
    private final TenantOutboundResilienceRuntime outboundResilienceRuntime;
    private final OutboundTenantUpstreamQuarantine outboundTenantUpstreamQuarantine;

    @Override
    public void streamCompletion(ModelChatRequest request, Consumer<String> onToken) throws Exception {
        streamCompletionWithResult(request, onToken);
    }

    @Override
    public ModelStreamResult streamCompletionWithResult(ModelChatRequest request, Consumer<String> onToken)
            throws Exception {
        String alias =
                request.getModelAlias() == null || request.getModelAlias().isBlank()
                        ? "mock"
                        : request.getModelAlias().trim();
        Long tenantId = request.getTenantId();
        if (tenantId == null) {
            var snap = TenantContextHolder.getOrNull();
            if (snap != null) {
                tenantId = snap.getTenantId();
            }
        }
        if ("mock".equalsIgnoreCase(alias)) {
            StringBuilder buf = new StringBuilder();
            runMock(request, t -> {
                buf.append(t);
                onToken.accept(t);
            });
            if (tenantId != null && tenantId > 0) {
                outboundTenantUpstreamQuarantine.recordTenantSuccess(tenantId);
            }
            return ModelStreamResult.builder().content(buf.toString()).finishReason("stop").build();
        }
        if (tenantId == null) {
            throw new IllegalStateException("缺少 tenantId，无法解析模型配置");
        }
        OpenAiStreamCapture capture = new OpenAiStreamCapture();
        List<SysLlmModel> chain = buildLanguageModelChain(tenantId, alias);
        if (chain.isEmpty()) {
            throw new IllegalArgumentException(
                    "未找到该租户下的大模型配置（模型别名："
                            + alias
                            + "）。请先在管理端为租户配置并启用 llm_model 后再发起对话。");
        }
        Exception last = null;
        for (int i = 0; i < chain.size(); i++) {
            SysLlmModel m = chain.get(i);
            try {
                if (request.getResolvedLlmModelIdRef() != null) {
                    request.getResolvedLlmModelIdRef().set(m.getId());
                }
                LlmModelKindPolicy.assertLanguageModelForChatStream(m);
                if (m.getApiKeyCipher() == null || m.getApiKeyCipher().isBlank()) {
                    throw new IllegalStateException("模型未配置 API Key：" + m.getAlias());
                }
                String apiKey = aesSecretCipher.decryptFromBase64(m.getApiKeyCipher());
                String json = buildOpenAiJsonBody(request, m.getOpenaiModelId(), true);
                Consumer<String> reasoning =
                        Boolean.TRUE.equals(request.getThinkingEnabled())
                                        && request.getReasoningTokenConsumer() != null
                                ? request.getReasoningTokenConsumer()
                                : null;
                Consumer<JsonNode> onUsage = usageCallback(request);
                openAiUpstreamStreamService.streamChat(
                        m.getOpenaiBaseUrl(),
                        apiKey,
                        json,
                        onToken,
                        reasoning,
                        onUsage,
                        new OpenAiCallContext(
                                tenantId, m.getAlias(), m.getId(), m.getOpenaiModelId(), m.getOpenaiBaseUrl()),
                        m.getAlias(),
                        request.getStreamCancelled(),
                        capture);
                outboundTenantUpstreamQuarantine.recordTenantSuccess(tenantId);
                return capture.toResult();
            } catch (Exception e) {
                last = e;
                outboundTenantUpstreamQuarantine.recordHardFailureIfSignal(tenantId, e);
                if (i == chain.size() - 1 || !allowsFailover(e, tenantId)) {
                    throw e;
                }
                log.warn(
                        "llm upstream failed, trying fallback tenantId={} failedModelId={} failedAlias={} cause={}",
                        tenantId,
                        m.getId(),
                        m.getAlias(),
                        e.toString());
            }
        }
        if (last != null) {
            throw last;
        }
        return capture.toResult();
    }

    private List<SysLlmModel> buildLanguageModelChain(long tenantId, String primaryAlias) {
        List<SysLlmModel> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String next = primaryAlias;
        for (int hop = 0; hop < 8 && next != null && !next.isBlank(); hop++) {
            String key = next.trim();
            if (seen.contains(key)) {
                break;
            }
            seen.add(key);
            var opt = llmModelRepository.findByTenantAndAlias(tenantId, key);
            if (opt.isEmpty()) {
                break;
            }
            SysLlmModel m = opt.get();
            out.add(m);
            String fb = m.getFallbackModelAlias();
            next = (fb == null || fb.isBlank()) ? null : fb.trim();
        }
        return out;
    }

    private boolean allowsFailover(Exception e, long tenantId) {
        AiOutboundResilienceProperties outboundProps = outboundResilienceRuntime.effective(tenantId);
        if (e instanceof IllegalArgumentException) {
            return false;
        }
        if (e instanceof LlmOutboundException lo) {
            Integer st = lo.getHttpStatus();
            if (st == null) {
                return true;
            }
            if (OutboundRetrySupport.isRetryableHttpStatus(outboundProps, st)) {
                return true;
            }
            if (st >= 500) {
                return true;
            }
            if (st == 401 || st == 403) {
                return true;
            }
            return false;
        }
        if (e instanceof JsonProcessingException) {
            return true;
        }
        return OutboundRetrySupport.isRetryableException(outboundProps, e);
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
                String role = t.getRole() == null ? "user" : t.getRole();
                m.put("role", role);
                if ("tool".equalsIgnoreCase(role)) {
                    if (t.getToolCallId() != null) {
                        m.put("tool_call_id", t.getToolCallId());
                    }
                    if (t.getName() != null) {
                        m.put("name", t.getName());
                    }
                    m.put("content", t.getContent() == null ? "" : t.getContent());
                } else if ("assistant".equalsIgnoreCase(role) && t.getToolCalls() != null && !t.getToolCalls().isEmpty()) {
                    if (t.getContent() != null) {
                        m.put("content", t.getContent());
                    } else {
                        m.putNull("content");
                    }
                    ArrayNode tcs = m.putArray("tool_calls");
                    for (ModelToolCall tc : t.getToolCalls()) {
                        ObjectNode tcNode = tcs.addObject();
                        if (tc.getId() != null) {
                            tcNode.put("id", tc.getId());
                        }
                        tcNode.put("type", tc.getType() == null ? "function" : tc.getType());
                        ObjectNode fn = tcNode.putObject("function");
                        if (tc.getFunction() != null) {
                            fn.put("name", tc.getFunction().getName() == null ? "" : tc.getFunction().getName());
                            fn.put(
                                    "arguments",
                                    tc.getFunction().getArguments() == null ? "{}" : tc.getFunction().getArguments());
                        }
                    }
                } else {
                    m.put("content", t.getContent() == null ? "" : t.getContent());
                }
            }
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ModelToolDefinition td : request.getTools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", td.getType() == null ? "function" : td.getType());
                if (td.getFunction() != null) {
                    ObjectNode fn = tool.putObject("function");
                    fn.put("name", td.getFunction().getName());
                    if (td.getFunction().getDescription() != null) {
                        fn.put("description", td.getFunction().getDescription());
                    }
                    if (td.getFunction().getParameters() != null) {
                        fn.set("parameters", td.getFunction().getParameters());
                    }
                }
            }
            if (request.getToolChoice() != null && !request.getToolChoice().isBlank()) {
                body.put("tool_choice", request.getToolChoice());
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
