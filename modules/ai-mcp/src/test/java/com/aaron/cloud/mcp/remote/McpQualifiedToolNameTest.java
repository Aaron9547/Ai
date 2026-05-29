package com.aaron.cloud.mcp.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class McpQualifiedToolNameTest {

    @Test
    void qualifyAndParse() {
        String q = McpQualifiedToolName.qualify("jina", "search_web");
        assertEquals("jina::search_web", q);
        var p = McpQualifiedToolName.parse(q);
        assertEquals("jina", p.serverName());
        assertEquals("search_web", p.toolName());
    }

    @Test
    void parseRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> McpQualifiedToolName.parse("no-separator"));
    }
}
