package com.aaron.cloud.mcp.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class McpRemoteClientFactoryUrlTest {

    @Test
    void parseJinaBaseUrl() {
        var parsed = McpRemoteClientFactory.parseBaseUrl("https://mcp.jina.ai/v1?exclude_tags=parallel");
        assertEquals("https://mcp.jina.ai", parsed.origin());
        assertEquals("/v1?exclude_tags=parallel", parsed.endpoint());
    }

    @Test
    void parseWithPort() {
        var parsed = McpRemoteClientFactory.parseBaseUrl("http://localhost:8080/mcp");
        assertEquals("http://localhost:8080", parsed.origin());
        assertEquals("/mcp", parsed.endpoint());
    }
}
