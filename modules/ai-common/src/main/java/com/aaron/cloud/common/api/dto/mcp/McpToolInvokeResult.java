package com.aaron.cloud.common.api.dto.mcp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpToolInvokeResult {

    private String qualifiedName;
    private long serverId;
    private String toolName;
    private boolean error;
    private String textContent;
    private long elapsedMs;
}
