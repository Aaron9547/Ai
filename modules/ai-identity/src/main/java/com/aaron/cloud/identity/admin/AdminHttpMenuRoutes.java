package com.aaron.cloud.identity.admin;

import com.aaron.cloud.common.api.enums.gateway.AdminMenuCode;

public final class AdminHttpMenuRoutes {

    private AdminHttpMenuRoutes() {}

    /** 将请求 URI 映射到所需菜单码；未映射的 admin 路径返回 null（拒绝）。 */
    public static AdminMenuCode resolve(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.startsWith("/api/v1/admin/dashboard")) {
            return AdminMenuCode.DASHBOARD;
        }
        if (uri.startsWith("/api/v1/admin/user-profiles")) {
            return AdminMenuCode.USER_PROFILES;
        }
        if (uri.startsWith("/api/v1/admin/users")) {
            return AdminMenuCode.USERS;
        }
        if (uri.startsWith("/api/v1/admin/tenant-members")) {
            return AdminMenuCode.USERS;
        }
        if (uri.startsWith("/api/v1/admin/tenants")) {
            return AdminMenuCode.TENANTS;
        }
        if (uri.startsWith("/api/v1/admin/access-logs")) {
            return AdminMenuCode.ACCESS_LOGS;
        }
        if (uri.startsWith("/api/v1/admin/audit-events")) {
            return AdminMenuCode.AUDIT_EVENTS;
        }
        if (uri.startsWith("/api/v1/admin/metering-events")) {
            return AdminMenuCode.METERING;
        }
        if (uri.startsWith("/api/v1/admin/file-objects")) {
            return AdminMenuCode.FILE_OBJECTS;
        }
        if (uri.startsWith("/api/v1/admin/notification-subscriptions")) {
            return AdminMenuCode.NOTIFICATIONS;
        }
        if (uri.startsWith("/api/v1/admin/eval-runs")) {
            return AdminMenuCode.EVAL_RUNS;
        }
        if (uri.startsWith("/api/v1/admin/llm-models")) {
            return AdminMenuCode.LLM_MODELS;
        }
        if (uri.startsWith("/api/v1/admin/prompt-templates")) {
            return AdminMenuCode.PROMPT_TEMPLATES;
        }
        if (uri.startsWith("/api/v1/admin/mcp-servers")) {
            return AdminMenuCode.MCP_SERVERS;
        }
        if (uri.startsWith("/api/v1/admin/rag-kbs")) {
            return AdminMenuCode.RAG_KBS;
        }
        if (uri.startsWith("/api/v1/admin/job-tasks")) {
            return AdminMenuCode.RAG_KBS;
        }
        if (uri.startsWith("/api/v1/admin/scheduled-tasks")) {
            return AdminMenuCode.SCHEDULED_TASKS;
        }
        if (uri.startsWith("/api/v1/admin/message/")) {
            return AdminMenuCode.MESSAGE_CENTER;
        }
        if (uri.startsWith("/api/v1/admin/chat/intents")) {
            return AdminMenuCode.CHAT_INTENTS;
        }
        if (uri.startsWith("/api/v1/admin/chat")) {
            return AdminMenuCode.CHAT;
        }
        if (uri.startsWith("/api/v1/admin/tenant-shell-config")
                || uri.startsWith("/api/v1/admin/tenant-runtime-settings")) {
            return AdminMenuCode.SYSTEM_SETTINGS;
        }
        if (uri.startsWith("/api/v1/admin/menu-items")) {
            return AdminMenuCode.MENU_CATALOG;
        }
        if (uri.startsWith("/api/v1/admin/gateway-rate-limits")) {
            return AdminMenuCode.GATEWAY_API;
        }
        if (uri.startsWith("/api/v1/admin/gateway-api-endpoints")) {
            return AdminMenuCode.GATEWAY_API;
        }
        if (uri.startsWith("/api/v1/admin/cors-allowed-origins")) {
            return AdminMenuCode.GATEWAY_API;
        }
        if (uri.startsWith("/api/v1/admin/gateway-access-parties")
                || uri.startsWith("/api/v1/admin/gateway-api-modules")
                || uri.startsWith("/api/v1/admin/gateway-access-party-grants")
                || uri.startsWith("/api/v1/admin/gateway-access-party-audit")) {
            return AdminMenuCode.GATEWAY_API;
        }
        return null;
    }
}
