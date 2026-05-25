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
    void unknownAdminPathReturnsNull() {
        assertNull(AdminHttpMenuRoutes.resolve("/api/v1/admin/unknown-resource"));
    }
}
