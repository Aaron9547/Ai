package com.aaron.cloud.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientIpRegionLookupTest {

    @Test
    void parseSearchResult_readsIsoSuffix() {
        String region = ClientIpRegionLookup.parseSearchResult("中国|广东省|深圳市|电信|CN");
        assertNotNull(region);
        assertTrue(region.startsWith("CN|"));
    }

    @Test
    void resolveRegion_skipsLoopback() {
        assertNull(ClientIpRegionLookup.resolveRegion("127.0.0.1"));
        assertNull(ClientIpRegionLookup.resolveRegion("10.0.0.1"));
    }

    @Test
    void resolveRegion_publicIpWhenXdbPresent() {
        String region = ClientIpRegionLookup.resolveRegion("8.8.8.8");
        if (region != null) {
            assertEquals(2, region.split("\\|")[0].length());
        }
    }

    @Test
    void isPrivateOrLocal_detectsCommonRanges() {
        assertTrue(ClientIpRegionLookup.isPrivateOrLocal("127.0.0.1"));
        assertTrue(ClientIpRegionLookup.isPrivateOrLocal("192.168.1.1"));
    }
}
