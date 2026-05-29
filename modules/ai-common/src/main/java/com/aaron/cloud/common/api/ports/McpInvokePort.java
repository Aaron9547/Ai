package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.mcp.McpServerProbeResult;
import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 跨模块 MCP 工具发现与调用端口（由 {@code ai-mcp} 实现）。 */
public interface McpInvokePort {

    List<McpToolDescriptor> listActiveTools(long tenantId, List<Long> serverIds);

    McpToolInvokeResult invokeTool(long tenantId, String qualifiedName, JsonNode arguments) throws Exception;

    McpServerProbeResult probeServer(long tenantId, long serverId) throws Exception;
}
