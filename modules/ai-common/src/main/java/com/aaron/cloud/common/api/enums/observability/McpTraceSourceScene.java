package com.aaron.cloud.common.api.enums.observability;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** MCP 工具调用来源场景（落库 {@code obs_mcp_trace_event.source_scene}）。 */
@Getter
@RequiredArgsConstructor
public enum McpTraceSourceScene {
    CHAT_MCP_LOOP("CHAT_MCP_LOOP"),
    INTENT_SINGLE("INTENT_SINGLE"),
    DIRECT_API("DIRECT_API");

    @EnumValue
    private final String code;
}
