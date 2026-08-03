package com.aaron.cloud.mcp.dto;

import com.aaron.cloud.common.api.enums.mcp.McpTransportKind;
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

        private McpTransportKind transportKind = McpTransportKind.STREAMABLE_HTTP;

        @Size(max = 512)
        private String description;

        /** 明文 API Key；服务端存为 Authorization Bearer 密文 */
        @Size(max = 512)
        private String apiKey;

        private boolean enabled = true;
    }

    @Data
    public static class UpdateMcpServerRequest {
        @Size(max = 128)
        private String name;

        @Size(max = 1024)
        private String baseUrl;

        private McpTransportKind transportKind;

        @Size(max = 512)
        private String description;

        /** 留空表示不修改密钥 */
        @Size(max = 512)
        private String apiKey;

        private Boolean enabled;
    }

    public record McpServerAdminView(
            long id,
            long tenantId,
            String name,
            String baseUrl,
            String transportKind,
            String description,
            boolean hasApiKey,
            String status,
            String lastProbeAt,
            Boolean lastProbeOk,
            String createdAt,
            String updatedAt) {}

    public record McpServerProbeView(boolean ok, String message, int toolCount, long elapsedMs) {}
}
