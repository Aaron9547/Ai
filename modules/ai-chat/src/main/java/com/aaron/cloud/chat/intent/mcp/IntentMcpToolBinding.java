package com.aaron.cloud.chat.intent.mcp;

import com.aaron.cloud.common.api.ports.McpInvokePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record IntentMcpToolBinding(String qualifiedName) {

    private static final Logger log = LoggerFactory.getLogger(IntentMcpToolBinding.class);

    public void assertToolAvailable(McpInvokePort port, long tenantId) throws Exception {
        if (!port.isToolAvailable(tenantId, qualifiedName)) {
            log.warn(
                    "[意图·MCP] 工具未就绪 tenantId={} qualifiedName={}（检查内置 Bean 是否加载或意图 parseToolKind 配置）",
                    tenantId,
                    qualifiedName);
            throw new IllegalStateException("MCP 工具未就绪：" + qualifiedName);
        }
    }
}
