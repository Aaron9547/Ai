package com.aaron.cloud.mcp.rest.api;

import com.aaron.cloud.common.api.dto.mcp.McpToolDescriptor;
import com.aaron.cloud.common.api.dto.mcp.McpToolInvokeResult;
import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.mcp.McpApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class McpServerToolsRestController extends ApiV1ControllerBases.McpServersApi {

    private final McpApplicationService mcpApplicationService;

    @GetMapping("/{serverId}/tools")
    public List<McpToolDescriptor> listTools(@PathVariable long serverId) throws Exception {
        return mcpApplicationService.listServerTools(serverId, Duration.ofSeconds(60));
    }

    @PostMapping("/{serverId}/tools/{name}/invoke")
    public McpToolInvokeResult invoke(
            @PathVariable long serverId,
            @PathVariable String name,
            @RequestBody InvokeBody body)
            throws Exception {
        JsonNode args =
                body == null || body.getArguments() == null
                        ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        : body.getArguments();
        return mcpApplicationService.invokeServerTool(serverId, name, args, Duration.ofSeconds(60));
    }

    @Data
    public static class InvokeBody {
        private JsonNode arguments;
    }
}
