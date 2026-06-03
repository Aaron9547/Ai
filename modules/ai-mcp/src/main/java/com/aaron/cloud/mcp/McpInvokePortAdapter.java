package com.aaron.cloud.mcp;

import com.aaron.cloud.common.api.dto.mcp.McpServerProbeResult;
import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.api.ports.McpInvokePort;
import com.aaron.cloud.mcp.builtin.BuiltinMcpToolRegistry;
import com.aaron.cloud.mcp.remote.McpQualifiedToolName;
import com.aaron.cloud.mcp.remote.McpRemoteSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpInvokePortAdapter implements McpInvokePort {

    private final McpRemoteSessionService mcpRemoteSessionService;
    private final BuiltinMcpToolRegistry builtinMcpToolRegistry;
    private final McpInvokeTimeouts mcpInvokeTimeouts;

    @Override
    public List<McpToolDescriptor> listActiveTools(long tenantId, List<Long> serverIds) {
        try {
            List<McpToolDescriptor> merged = new ArrayList<>(builtinMcpToolRegistry.listDescriptors());
            List<McpToolDescriptor> remote =
                    mcpRemoteSessionService.listTools(tenantId, serverIds, mcpInvokeTimeouts.toolTimeout(tenantId));
            for (McpToolDescriptor d : remote) {
                if (d.getQualifiedName() != null
                        && builtinMcpToolRegistry.isBuiltinQualifiedName(d.getQualifiedName())) {
                    continue;
                }
                merged.add(d);
            }
            return merged;
        } catch (Exception e) {
            throw new IllegalStateException("list mcp tools failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isToolAvailable(long tenantId, String qualifiedName) throws Exception {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return false;
        }
        String q = qualifiedName.trim();
        if (builtinMcpToolRegistry.isBuiltinQualifiedName(q)) {
            return true;
        }
        var parsed = McpQualifiedToolName.parse(q);
        List<McpToolDescriptor> remote =
                mcpRemoteSessionService.listToolsForServerName(
                        tenantId, parsed.serverName(), mcpInvokeTimeouts.toolTimeout(tenantId));
        return remote.stream()
                .anyMatch(d -> d.getQualifiedName() != null && d.getQualifiedName().equals(q));
    }

    @Override
    public McpToolInvokeResult invokeTool(long tenantId, String qualifiedName, JsonNode arguments)
            throws Exception {
        Optional<McpToolInvokeResult> builtin = builtinMcpToolRegistry.tryInvoke(qualifiedName, arguments);
        if (builtin.isPresent()) {
            return builtin.get();
        }
        return mcpRemoteSessionService.invokeTool(
                tenantId, qualifiedName, arguments, mcpInvokeTimeouts.toolTimeout(tenantId));
    }

    @Override
    public McpServerProbeResult probeServer(long tenantId, long serverId) throws Exception {
        return mcpRemoteSessionService.probe(tenantId, serverId, mcpInvokeTimeouts.toolTimeout(tenantId));
    }
}
