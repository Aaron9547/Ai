package com.aaron.cloud.gateway.accessparty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.gateway.AccessPartyHttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccessPartyFilterSkipPolicyTest {

    @Test
    void skipsAdminApiPaths() {
        var req = new MockHttpServletRequest("GET", "/api/v1/admin/gateway-access-parties");
        assertTrue(AccessPartyFilterSkipPolicy.shouldSkip(req));
        assertFalse(AccessPartyFilterSkipPolicy.requiresAccessPartyChain(req));
    }

    @Test
    void skipsUnsignedOpenPaths() {
        var req = new MockHttpServletRequest("POST", "/open/v1/chat/completions");
        assertTrue(AccessPartyFilterSkipPolicy.shouldSkip(req));
        assertFalse(AccessPartyFilterSkipPolicy.requiresAccessPartyChain(req));
    }

    @Test
    void requiresChainForPartnerPaths() {
        var req = new MockHttpServletRequest("GET", "/partner/v1/health");
        assertFalse(AccessPartyFilterSkipPolicy.shouldSkip(req));
        assertTrue(AccessPartyFilterSkipPolicy.requiresAccessPartyChain(req));
    }

    @Test
    void requiresChainForSignedOpenPaths() {
        var req = new MockHttpServletRequest("POST", "/open/v1/chat/completions");
        req.addHeader(AccessPartyHttpHeaders.APP_ID, "demo-app");
        req.addHeader(AccessPartyHttpHeaders.SIGNATURE, "sig");
        assertFalse(AccessPartyFilterSkipPolicy.shouldSkip(req));
        assertTrue(AccessPartyFilterSkipPolicy.requiresAccessPartyChain(req));
    }

    @Test
    void skipsActuatorAndOptions() {
        var actuator = new MockHttpServletRequest("GET", "/actuator/health");
        assertTrue(AccessPartyFilterSkipPolicy.shouldSkip(actuator));

        var options = new MockHttpServletRequest("OPTIONS", "/partner/v1/health");
        assertTrue(AccessPartyFilterSkipPolicy.shouldSkip(options));
    }
}
