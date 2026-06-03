package com.aaron.cloud.chat.mcp.builtin;

import com.aaron.cloud.chat.reminder.ReminderParseLlmService;
import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.enums.chat.ReminderParseToolKind;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import com.aaron.cloud.mcp.builtin.BuiltinMcpServerNames;
import com.aaron.cloud.mcp.builtin.BuiltinMcpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParseReminderLlmBuiltinTool implements BuiltinMcpTool {

    public static final String TOOL_NAME = "parse_reminder_llm";
    public static final String QUALIFIED = ReminderParseToolKind.ParseReminderBuiltinTools.LLM;

    private final ReminderParseLlmService reminderParseLlmService;
    private final ObjectMapper objectMapper;

    @Override
    public McpToolDescriptor descriptor() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("contractVersion", objectMapper.createObjectNode().put("type", "integer"));
        properties.set("utterance", objectMapper.createObjectNode().put("type", "string"));
        properties.set("parseModelAlias", objectMapper.createObjectNode().put("type", "string"));
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        return McpToolDescriptor.builder()
                .serverId(0L)
                .serverName(BuiltinMcpServerNames.PLATFORM)
                .qualifiedName(QUALIFIED)
                .toolName(TOOL_NAME)
                .description("大模型解析用户提醒话术（contractVersion=1）")
                .inputSchema(schema)
                .build();
    }

    @Override
    public Optional<McpToolInvokeResult> tryInvoke(String qualifiedName, JsonNode arguments) {
        if (!QUALIFIED.equals(qualifiedName)) {
            return Optional.empty();
        }
        long t0 = System.currentTimeMillis();
        try {
            ReminderParseRequest req = objectMapper.treeToValue(arguments, ReminderParseRequest.class);
            if (req.contractVersion() == null
                    || req.contractVersion() != ReminderParseContracts.CONTRACT_VERSION) {
                return Optional.of(result(errorJson("unsupported contractVersion"), true, t0));
            }
            String modelAlias =
                    arguments.hasNonNull("parseModelAlias")
                            ? arguments.get("parseModelAlias").asText().strip()
                            : null;
            if (modelAlias != null && modelAlias.isEmpty()) {
                modelAlias = null;
            }
            ReminderParseResponse out = reminderParseLlmService.parse(req, modelAlias);
            String json = objectMapper.writeValueAsString(out);
            return Optional.of(result(json, false, t0));
        } catch (Exception e) {
            return Optional.of(
                    result(errorJson("invalid arguments: " + e.getMessage()), true, t0));
        }
    }

    private McpToolInvokeResult result(String text, boolean error, long t0) {
        return McpToolInvokeResult.builder()
                .qualifiedName(QUALIFIED)
                .serverId(0L)
                .toolName(TOOL_NAME)
                .error(error)
                .textContent(text)
                .elapsedMs(System.currentTimeMillis() - t0)
                .build();
    }

    private String errorJson(String msg) {
        try {
            return objectMapper.writeValueAsString(
                    new ReminderParseResponse(
                            ReminderParseContracts.CONTRACT_VERSION,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            msg));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{\"contractVersion\":1,\"error\":\"" + msg.replace("\"", "\\\"") + "\"}";
        }
    }
}
