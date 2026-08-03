package com.aaron.cloud.common.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SafeOutboundUrlGuardTest {

    @Test
    void requireHttpOrHttps_acceptsPublicHttps() {
        URI uri = SafeOutboundUrlGuard.requireHttpOrHttps("https://example.com/a.jpg");
        assertEquals("example.com", uri.getHost());
    }

    @Test
    void requireHttpOrHttps_rejectsFileScheme() {
        assertThrows(IllegalArgumentException.class, () -> SafeOutboundUrlGuard.requireHttpOrHttps("file:///etc/passwd"));
    }

    @Test
    void requireHttpOrHttps_rejectsLoopbackLiteral() {
        assertThrows(IllegalArgumentException.class, () -> SafeOutboundUrlGuard.requireHttpOrHttps("http://127.0.0.1/x.jpg"));
    }

    @Test
    void isPrivateOrLocalLiteral_detectsCommonRanges() {
        assertTrue(SafeOutboundUrlGuard.isPrivateOrLocalLiteral("10.0.0.1"));
        assertTrue(SafeOutboundUrlGuard.isPrivateOrLocalLiteral("192.168.0.1"));
    }
}
