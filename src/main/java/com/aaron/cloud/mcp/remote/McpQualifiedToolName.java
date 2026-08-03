package com.aaron.cloud.mcp.remote;

import lombok.experimental.UtilityClass;

/** 多 Server 工具名限定：{@code serverName::toolName}。 */
@UtilityClass
public final class McpQualifiedToolName {

    public static final String SEP = "::";

    public static String qualify(String serverName, String toolName) {
        return serverName.trim() + SEP + toolName.trim();
    }

    public static Parsed parse(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new IllegalArgumentException("qualified tool name required");
        }
        int idx = qualifiedName.indexOf(SEP);
        if (idx <= 0 || idx >= qualifiedName.length() - SEP.length()) {
            throw new IllegalArgumentException("invalid qualified tool name: " + qualifiedName);
        }
        return new Parsed(
                qualifiedName.substring(0, idx).trim(),
                qualifiedName.substring(idx + SEP.length()).trim());
    }

    public record Parsed(String serverName, String toolName) {}
}
