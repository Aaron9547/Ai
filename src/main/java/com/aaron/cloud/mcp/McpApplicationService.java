package com.aaron.cloud.mcp;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpApplicationService {

    private static final Set<String> ALLOWED = Set.of("echo");

    private final ObjectMapper objectMapper;

    public JsonNode invokeTool(String toolName, JsonNode arguments) throws Exception {
        if (!ALLOWED.contains(toolName)) {
            throw new IllegalArgumentException("tool not whitelisted");
        }
        var snap = TenantContextHolder.require();
        if ("echo".equals(toolName)) {
            return objectMapper.valueToTree(
                    Map.of("tenantId", snap.getTenantId(), "echo", arguments.toString()));
        }
        throw new IllegalStateException("unreachable");
    }
}
