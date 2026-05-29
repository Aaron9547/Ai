package com.aaron.cloud.mcp.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.McpServerProbeView;
import com.aaron.cloud.mcp.remote.McpRemoteSessionService;
import com.aaron.cloud.common.context.TenantContextHolder;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class McpServerProbeRestController extends ApiV1ControllerBases.McpServers {

    private final McpRemoteSessionService mcpRemoteSessionService;

    @PostMapping("/{id}/probe")
    public McpServerProbeView probe(@PathVariable long id) throws Exception {
        long tenantId = TenantContextHolder.require().getTenantId();
        var result = mcpRemoteSessionService.probe(tenantId, id, Duration.ofSeconds(60));
        return new McpServerProbeView(result.isOk(), result.getMessage(), result.getToolCount(), result.getElapsedMs());
    }
}
