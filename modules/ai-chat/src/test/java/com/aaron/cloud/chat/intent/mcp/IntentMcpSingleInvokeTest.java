package com.aaron.cloud.chat.intent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpServerProbeResult;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.ports.McpInvokePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentMcpSingleInvokeTest {

    @Test
    void invokeOnce_mcpErrorFlagWithContractJson_surfacesBusinessError() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String body =
                "{\"contractVersion\":1,\"error\":\"请说明提醒时间，例如「每天8点」\"}";
        var result =
                IntentMcpSingleInvoke.invokeOnce(
                        portReturning(body, true),
                        1L,
                        new IntentMcpToolBinding("platform::parse_reminder"),
                        mapper.createObjectNode(),
                        mapper);
        assertFalse(result.failed());
        assertEquals(
                "请说明提醒时间，例如「每天8点」",
                result.body().get("error").asText());
    }

    @Test
    void invokeOnce_mcpErrorFlagWithPlainText_showsBodyToUser() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String body = "upstream timeout while listing tools";
        var result =
                IntentMcpSingleInvoke.invokeOnce(
                        portReturning(body, true),
                        1L,
                        new IntentMcpToolBinding("platform::parse_reminder"),
                        mapper.createObjectNode(),
                        mapper);
        assertTrue(result.failed());
        assertEquals(body, result.userFacingMessage());
    }

    @Test
    void invokeOnce_mcpErrorFlagWithoutJson_usesBodyNotGenericLabel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String body = "invalid arguments: utterance required";
        var result =
                IntentMcpSingleInvoke.invokeOnce(
                        portReturning(body, true),
                        1L,
                        new IntentMcpToolBinding("platform::parse_reminder"),
                        mapper.createObjectNode(),
                        mapper);
        assertTrue(result.failed());
        assertEquals(body, result.userFacingMessage());
        assertFalse(result.userFacingMessage().contains("MCP 工具返回错误"));
    }

    private static McpInvokePort portReturning(String body, boolean errorFlag) {
        return new McpInvokePort() {
            @Override
            public List<McpToolDescriptor> listActiveTools(long tenantId, List<Long> serverIds) {
                return List.of();
            }

            @Override
            public boolean isToolAvailable(long tenantId, String qualifiedName) {
                return true;
            }

            @Override
            public McpToolInvokeResult invokeTool(
                    long tenantId, String qualifiedName, com.fasterxml.jackson.databind.JsonNode arguments) {
                return McpToolInvokeResult.builder()
                        .qualifiedName(qualifiedName)
                        .error(errorFlag)
                        .textContent(body)
                        .build();
            }

            @Override
            public McpServerProbeResult probeServer(long tenantId, long serverId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
