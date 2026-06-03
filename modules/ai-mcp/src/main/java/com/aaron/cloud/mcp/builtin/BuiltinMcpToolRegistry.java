package com.aaron.cloud.mcp.builtin;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BuiltinMcpToolRegistry {

    private final Map<String, BuiltinMcpTool> byQualifiedName;

    public BuiltinMcpToolRegistry(List<BuiltinMcpTool> tools) {
        Map<String, BuiltinMcpTool> m = new HashMap<>();
        for (BuiltinMcpTool t : tools) {
            String q = t.descriptor().getQualifiedName();
            if (m.put(q, t) != null) {
                throw new IllegalStateException("duplicate builtin mcp tool: " + q);
            }
        }
        this.byQualifiedName = Map.copyOf(m);
    }

    public List<McpToolDescriptor> listDescriptors() {
        return new ArrayList<>(byQualifiedName.values().stream().map(BuiltinMcpTool::descriptor).toList());
    }

    public Optional<McpToolInvokeResult> tryInvoke(String qualifiedName, JsonNode arguments) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return Optional.empty();
        }
        BuiltinMcpTool tool = byQualifiedName.get(qualifiedName.trim());
        if (tool == null) {
            return Optional.empty();
        }
        return tool.tryInvoke(qualifiedName.trim(), arguments);
    }

    public boolean isBuiltinQualifiedName(String qualifiedName) {
        return qualifiedName != null && byQualifiedName.containsKey(qualifiedName.trim());
    }
}
