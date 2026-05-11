package com.aaron.cloud.common.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Chat 编排层意图路由占位（非库列枚举，供编排分支使用）。 */
@Getter
@RequiredArgsConstructor
public enum IntentRoute {
    CHAT_ONLY,
    RAG,
    MCP_TOOLS
}
