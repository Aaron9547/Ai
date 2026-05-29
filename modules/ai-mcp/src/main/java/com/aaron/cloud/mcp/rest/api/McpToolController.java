package com.aaron.cloud.mcp.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.mcp.McpApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class McpToolController extends ApiV1ControllerBases.McpTools {

    private final McpApplicationService mcpApplicationService;

    @PostMapping("/{name}/invoke")
    public JsonNode invoke(@PathVariable String name, @RequestBody JsonNode body) throws Exception {
        return mcpApplicationService.invokeToolLegacy(name, body);
    }
}
