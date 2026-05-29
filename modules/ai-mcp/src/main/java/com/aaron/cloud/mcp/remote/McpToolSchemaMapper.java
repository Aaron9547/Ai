package com.aaron.cloud.mcp.remote;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.mcp.entity.McpServerRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class McpToolSchemaMapper {

    private final ObjectMapper objectMapper;

    public List<McpToolDescriptor> toDescriptors(McpServerRegistry server, List<Tool> tools) {
        List<McpToolDescriptor> out = new ArrayList<>();
        if (tools == null) {
            return out;
        }
        for (Tool tool : tools) {
            JsonNode schema = tool.inputSchema() == null
                    ? objectMapper.createObjectNode()
                    : objectMapper.valueToTree(tool.inputSchema());
            out.add(McpToolDescriptor.builder()
                    .serverId(server.getId())
                    .serverName(server.getName())
                    .qualifiedName(McpQualifiedToolName.qualify(server.getName(), tool.name()))
                    .toolName(tool.name())
                    .description(tool.description())
                    .inputSchema(schema)
                    .build());
        }
        return out;
    }
}
