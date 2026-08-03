package com.aaron.cloud.mcp.builtin.tool;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseRequest;
import com.aaron.cloud.common.api.mcp.reminder.ReminderParseContracts.ReminderParseResponse;
import com.aaron.cloud.mcp.builtin.BuiltinMcpServerNames;
import com.aaron.cloud.mcp.builtin.BuiltinMcpTool;
import com.aaron.cloud.mcp.builtin.reminder.ReminderParseService;
import com.aaron.cloud.mcp.remote.McpQualifiedToolName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseReminderBuiltinTool implements BuiltinMcpTool {

    public static final String TOOL_NAME = "parse_reminder";
    public static final String QUALIFIED =
            McpQualifiedToolName.qualify(BuiltinMcpServerNames.PLATFORM, TOOL_NAME);

    private final ReminderParseService reminderParseService;
    private final ObjectMapper objectMapper;

    @Override
    public McpToolDescriptor descriptor() {
        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("contractVersion", objectMapper.createObjectNode().put("type", "integer"));
        properties.set("action", objectMapper.createObjectNode().put("type", "string"));
        properties.set("utterance", objectMapper.createObjectNode().put("type", "string"));
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        return McpToolDescriptor.builder()
                .serverId(0L)
                .serverName(BuiltinMcpServerNames.PLATFORM)
                .qualifiedName(QUALIFIED)
                .toolName(TOOL_NAME)
                .description("解析用户提醒话术，返回创建/取消提醒的结构化结果（contractVersion=1）")
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
            ReminderParseResponse out = reminderParseService.parse(req);
            String json = objectMapper.writeValueAsString(out);
            if (out.error() != null && !out.error().isBlank()) {
                log.info(
                        "[内置MCP·parse_reminder] 规则解析业务失败 action={} error={} utterance={}",
                        req.action(),
                        out.error(),
                        abbreviate(req.utterance(), 80));
            } else {
                log.info(
                        "[内置MCP·parse_reminder] 规则解析成功 op={} cron={} title={}",
                        out.op(),
                        out.cronExpression(),
                        out.title());
            }
            // 业务失败写在 JSON {@code error} 字段，勿标记 MCP 协议层 error（否则意图层只显示「MCP 工具返回错误」）
            return Optional.of(result(json, false, t0));
        } catch (Exception e) {
            log.warn("[内置MCP·parse_reminder] 参数或解析异常: {}", e.toString());
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

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
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
