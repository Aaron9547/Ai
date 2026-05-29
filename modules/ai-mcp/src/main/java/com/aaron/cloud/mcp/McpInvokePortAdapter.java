package com.aaron.cloud.mcp;

import com.aaron.cloud.common.api.dto.mcp.McpServerProbeResult;
import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.api.ports.McpInvokePort;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.mcp.remote.McpRemoteSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpInvokePortAdapter implements McpInvokePort {

    private final McpRemoteSessionService mcpRemoteSessionService;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;

    @Override
    public List<McpToolDescriptor> listActiveTools(long tenantId, List<Long> serverIds) {
        try {
            return mcpRemoteSessionService.listTools(tenantId, serverIds, toolTimeout(tenantId));
        } catch (Exception e) {
            throw new IllegalStateException("list mcp tools failed: " + e.getMessage(), e);
        }
    }

    @Override
    public McpToolInvokeResult invokeTool(long tenantId, String qualifiedName, JsonNode arguments)
            throws Exception {
        return mcpRemoteSessionService.invokeTool(tenantId, qualifiedName, arguments, toolTimeout(tenantId));
    }

    @Override
    public McpServerProbeResult probeServer(long tenantId, long serverId) throws Exception {
        return mcpRemoteSessionService.probe(tenantId, serverId, toolTimeout(tenantId));
    }

    private Duration toolTimeout(long tenantId) {
        String raw = tenantRuntimeSettingApplicationService.getEffectiveValueText(
                tenantId, TenantRuntimeSettingKey.MCP_CHAT_TOOL_TIMEOUT_SECONDS);
        int sec;
        try {
            sec = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            sec = 60;
        }
        if (sec < 5) {
            sec = 5;
        }
        if (sec > 300) {
            sec = 300;
        }
        return Duration.ofSeconds(sec);
    }
}
