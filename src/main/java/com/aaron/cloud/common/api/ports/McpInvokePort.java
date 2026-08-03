package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.mcp.McpServerProbeResult;
import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 跨模块 MCP 工具发现与调用端口（由 {@code ai-mcp} 实现）。 */
public interface McpInvokePort {

    List<McpToolDescriptor> listActiveTools(long tenantId, List<Long> serverIds);

    /**
     * 判断限定名是否可调用：内置工具仅查进程注册表；远程工具只探测对应 MCP 服务器，不枚举租户全部远程服务。
     */
    boolean isToolAvailable(long tenantId, String qualifiedName) throws Exception;

    McpToolInvokeResult invokeTool(long tenantId, String qualifiedName, JsonNode arguments) throws Exception;

    McpServerProbeResult probeServer(long tenantId, long serverId) throws Exception;
}
