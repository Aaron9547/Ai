package com.aaron.cloud.common.api.dto.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpToolDescriptor {

    private long serverId;
    private String serverName;
    private String qualifiedName;
    private String toolName;
    private String description;
    private JsonNode inputSchema;
}
