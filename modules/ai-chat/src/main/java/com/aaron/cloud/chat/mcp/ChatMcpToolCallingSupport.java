package com.aaron.cloud.chat.mcp;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelStreamResult;
import com.aaron.cloud.common.api.dto.model.ModelToolCall;
import com.aaron.cloud.common.api.dto.model.ModelToolDefinition;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.api.ports.McpInvokePort;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMcpToolCallingSupport {

    private final McpInvokePort mcpInvokePort;
    private final ModelInvokePort modelInvokePort;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ObjectMapper objectMapper;

    public McpLoopResult streamWithToolLoop(
            long tenantId,
            List<Long> serverIds,
            ModelChatRequest modelReq,
            Consumer<String> onContentToken,
            BiConsumer<String, String> onMcpStatus)
            throws Exception {
        List<McpToolDescriptor> descriptors = mcpInvokePort.listActiveTools(tenantId, serverIds);
        if (descriptors.isEmpty()) {
            throw new IllegalStateException("未配置可用的 MCP Server，请先在管理端注册并启用");
        }
        modelReq.setTools(toOpenAiTools(descriptors));
        modelReq.setToolChoice("auto");

        int maxRounds = parseIntSetting(tenantId, TenantRuntimeSettingKey.MCP_CHAT_MAX_TOOL_ROUNDS, 5, 1, 10);
        int maxResultChars =
                parseIntSetting(tenantId, TenantRuntimeSettingKey.MCP_CHAT_TOOL_RESULT_MAX_CHARS, 8000, 500, 64_000);
        List<McpToolCallSummary> audit = new ArrayList<>();
        StringBuilder finalContent = new StringBuilder();

        for (int round = 0; round < maxRounds; round++) {
            ModelStreamResult result = modelInvokePort.streamCompletionWithResult(modelReq, onContentToken);
            if (result.getContent() != null && !result.getContent().isBlank()) {
                finalContent.setLength(0);
                finalContent.append(result.getContent());
            }
            boolean toolRound =
                    result.hasToolCalls()
                            || "tool_calls".equalsIgnoreCase(result.getFinishReason());
            if (!toolRound) {
                return McpLoopResult.builder()
                        .content(finalContent.toString())
                        .toolCallSummaries(audit)
                        .build();
            }
            var assistantTurn = new ModelChatRequest.MessageTurn();
            assistantTurn.setRole("assistant");
            assistantTurn.setContent(result.getContent());
            assistantTurn.setToolCalls(result.getToolCalls());
            modelReq.getMessages().add(assistantTurn);

            for (ModelToolCall tc : result.getToolCalls()) {
                String qName = tc.getFunction() == null ? null : tc.getFunction().getName();
                if (qName == null || qName.isBlank()) {
                    continue;
                }
                onMcpStatus.accept("calling", qName);
                JsonNode args = parseArgs(tc.getFunction().getArguments());
                long t0 = System.currentTimeMillis();
                McpToolInvokeResult invoked = mcpInvokePort.invokeTool(tenantId, qName, args);
                long elapsed = System.currentTimeMillis() - t0;
                String text = truncate(invoked.getTextContent(), maxResultChars);
                onMcpStatus.accept(invoked.isError() ? "error" : "done", qName);

                var toolTurn = new ModelChatRequest.MessageTurn();
                toolTurn.setRole("tool");
                toolTurn.setToolCallId(tc.getId());
                toolTurn.setName(qName);
                toolTurn.setContent(text);
                modelReq.getMessages().add(toolTurn);

                audit.add(McpToolCallSummary.builder()
                        .qualifiedName(qName)
                        .serverId(invoked.getServerId())
                        .toolName(invoked.getToolName())
                        .error(invoked.isError())
                        .elapsedMs(elapsed)
                        .build());
            }
        }
        throw new IllegalStateException("MCP 工具调用超过最大轮数 " + maxRounds);
    }

    private List<ModelToolDefinition> toOpenAiTools(List<McpToolDescriptor> descriptors) {
        List<ModelToolDefinition> out = new ArrayList<>();
        for (McpToolDescriptor d : descriptors) {
            out.add(ModelToolDefinition.builder()
                    .type("function")
                    .function(ModelToolDefinition.FunctionDef.builder()
                            .name(d.getQualifiedName())
                            .description(d.getDescription())
                            .parameters(d.getInputSchema())
                            .build())
                    .build());
        }
        return out;
    }

    private JsonNode parseArgs(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(raw);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…(truncated)";
    }

    private int parseIntSetting(
            long tenantId, TenantRuntimeSettingKey key, int defaultVal, int min, int max) {
        String raw = tenantRuntimeSettingApplicationService.getEffectiveValueText(tenantId, key);
        int v;
        try {
            v = Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            v = defaultVal;
        }
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    @Data
    @Builder
    public static class McpLoopResult {
        private String content;
        private List<McpToolCallSummary> toolCallSummaries;
    }

    @Data
    @Builder
    public static class McpToolCallSummary {
        private String qualifiedName;
        private long serverId;
        private String toolName;
        private boolean error;
        private long elapsedMs;
    }
}
