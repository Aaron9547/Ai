package com.aaron.cloud.common.api.dto.mcp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpServerProbeResult {

    private boolean ok;
    private String message;
    private int toolCount;
    private long elapsedMs;
}
