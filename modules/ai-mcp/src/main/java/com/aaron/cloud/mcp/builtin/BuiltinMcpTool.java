package com.aaron.cloud.mcp.builtin;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/** 进程内 MCP 工具；新增工具时实现本接口并注册为 Spring Bean。 */
public interface BuiltinMcpTool {

    McpToolDescriptor descriptor();

    Optional<McpToolInvokeResult> tryInvoke(String qualifiedName, JsonNode arguments);
}
