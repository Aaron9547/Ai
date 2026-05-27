package com.aaron.cloud.identity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aaron.cloud.common.api.enums.gateway.AdminMenuCode;
import org.junit.jupiter.api.Test;

class AdminHttpMenuRoutesTest {

    @Test
    void gatewayAccessPartyPathsRequireGatewayApiMenu() {
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-access-parties"));
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-access-parties/1/grant-wizard"));
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-access-parties/1/rotate-secret"));
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-api-endpoints/sync-openapi-spec"));
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-api-modules"));
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-access-party-grants"));
        assertEquals(
                AdminMenuCode.GATEWAY_API,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/gateway-access-party-audit/summary"));
    }

    @Test
    void messageCenterPathsRequireMessageCenterMenu() {
        assertEquals(AdminMenuCode.MESSAGE_CENTER, AdminHttpMenuRoutes.resolve("/api/v1/admin/message/channels"));
        assertEquals(AdminMenuCode.MESSAGE_CENTER, AdminHttpMenuRoutes.resolve("/api/v1/admin/message/templates/1"));
    }

    @Test
    void promptTemplatePathsRequirePromptTemplatesMenu() {
        assertEquals(
                AdminMenuCode.PROMPT_TEMPLATES,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/prompt-templates"));
        assertEquals(
                AdminMenuCode.PROMPT_TEMPLATES,
                AdminHttpMenuRoutes.resolve("/api/v1/admin/prompt-templates/cache/evict"));
    }

    @Test
    void unknownAdminPathReturnsNull() {
        assertNull(AdminHttpMenuRoutes.resolve("/api/v1/admin/unknown-resource"));
    }
}
