package com.aaron.cloud.common.api.enums.mcp;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** MCP Server 出站传输类型（存 {@code mcp_server_registry.transport_kind}）。 */
@Getter
@RequiredArgsConstructor
public enum McpTransportKind {
    /** 远程 Streamable HTTP（MCP 2025-03-26，如 Jina {@code https://mcp.jina.ai/v1}）。 */
    STREAMABLE_HTTP(1);

    @EnumValue private final int code;
}
