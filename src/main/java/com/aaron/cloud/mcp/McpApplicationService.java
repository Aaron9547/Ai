package com.aaron.cloud.mcp;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.mcp.McpServerRegistryRepository;
import com.aaron.cloud.mcp.remote.McpQualifiedToolName;
import com.aaron.cloud.mcp.remote.McpRemoteSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpApplicationService {

    private final McpRemoteSessionService mcpRemoteSessionService;
    private final McpServerRegistryRepository mcpServerRegistryRepository;
    private final ObjectMapper objectMapper;

    public List<McpToolDescriptor> listServerTools(long serverId, Duration timeout) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        return mcpRemoteSessionService.listTools(tenantId, List.of(serverId), timeout);
    }

    public McpToolInvokeResult invokeServerTool(
            long serverId, String toolName, JsonNode arguments, Duration timeout) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        var server = mcpServerRegistryRepository.findByIdAndTenant(serverId, tenantId);
        if (server == null) {
            throw new IllegalArgumentException("mcp server not found");
        }
        String qualified = McpQualifiedToolName.qualify(server.getName(), toolName);
        return mcpRemoteSessionService.invokeTool(tenantId, qualified, arguments, timeout);
    }

    /** 兼容旧 API：body 须含 serverId */
    public JsonNode invokeToolLegacy(String toolName, JsonNode body) throws Exception {
        if (body != null && body.has("serverId")) {
            long serverId = body.get("serverId").asLong();
            JsonNode args = body.has("arguments") ? body.get("arguments") : objectMapper.nullNode();
            McpToolInvokeResult r =
                    invokeServerTool(serverId, toolName, args, Duration.ofSeconds(60));
            return objectMapper.valueToTree(r);
        }
        if ("echo".equals(toolName)) {
            var snap = TenantContextHolder.require();
            return objectMapper.valueToTree(Map.of(
                    "tenantId", snap.getTenantId(),
                    "echo",
                    body == null ? "" : body.toString(),
                    "deprecated", true));
        }
        throw new IllegalArgumentException("serverId required in body.arguments wrapper");
    }
}
