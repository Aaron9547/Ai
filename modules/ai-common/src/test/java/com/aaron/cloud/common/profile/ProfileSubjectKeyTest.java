package com.aaron.cloud.common.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProfileSubjectKeyTest {

    @Test
    void tenantColdStartKeyIsStablePerTenant() {
        assertEquals("tc:42", ProfileSubjectKey.tenantColdStartKey(42));
    }
}
