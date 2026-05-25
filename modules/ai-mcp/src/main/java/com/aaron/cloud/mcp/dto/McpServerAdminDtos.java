package com.aaron.cloud.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public final class McpServerAdminDtos {

    private McpServerAdminDtos() {}

    @Data
    public static class CreateMcpServerRequest {
        @NotBlank
        @Size(max = 128)
        private String name;

        @NotBlank
        @Size(max = 1024)
        private String baseUrl;

        /** 默认启用 */
        private boolean enabled = true;
    }

    @Data
    public static class UpdateMcpServerRequest {
        @Size(max = 128)
        private String name;

        @Size(max = 1024)
        private String baseUrl;

        private Boolean enabled;
    }

    public record McpServerAdminView(
            long id,
            long tenantId,
            String name,
            String baseUrl,
            String status,
            String createdAt,
            String updatedAt) {}
}
