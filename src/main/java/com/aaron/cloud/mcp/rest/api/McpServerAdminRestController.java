package com.aaron.cloud.mcp.rest.api;

import com.aaron.cloud.common.web.rest.ApiV1ControllerBases;
import com.aaron.cloud.mcp.McpServerAdminApplicationService;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.CreateMcpServerRequest;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.McpServerAdminView;
import com.aaron.cloud.mcp.dto.McpServerAdminDtos.UpdateMcpServerRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class McpServerAdminRestController extends ApiV1ControllerBases.McpServers {

    private final McpServerAdminApplicationService mcpServerAdminApplicationService;

    @GetMapping
    public Page<McpServerAdminView> page(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return mcpServerAdminApplicationService.page(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public McpServerAdminView create(@Valid @RequestBody CreateMcpServerRequest body) {
        return mcpServerAdminApplicationService.create(body);
    }

    @PutMapping("/{id}")
    public McpServerAdminView update(@PathVariable long id, @Valid @RequestBody UpdateMcpServerRequest body) {
        return mcpServerAdminApplicationService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        mcpServerAdminApplicationService.delete(id);
    }
}
