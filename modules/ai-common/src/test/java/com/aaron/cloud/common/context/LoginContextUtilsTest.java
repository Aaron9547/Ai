package com.aaron.cloud.common.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.api.enums.identity.UserAccountStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoginContextUtilsTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void readsTenantAndUserFromHolders() {
        LoginUser user =
                LoginUser.builder()
                        .id(42L)
                        .loginName("alice")
                        .displayName("Alice")
                        .status(UserAccountStatus.ACTIVE)
                        .build();
        TenantContextHolder.set(
                TenantContextHolder.TenantSnapshot.builder()
                        .tenantId(7L)
                        .userId(42L)
                        .memberRole(TenantMemberRole.ADMIN)
                        .build(),
                user);

        assertEquals(7L, LoginContextUtils.requireTenantId());
        assertEquals(42L, LoginContextUtils.requireUserId());
        assertSame(user, LoginContextUtils.requireUser());
        assertEquals("Alice", LoginContextUtils.requireUser().displayLabel());
    }

    @Test
    void requireUserIdFailsWhenGuest() {
        TenantContextHolder.set(
                TenantContextHolder.TenantSnapshot.builder().tenantId(1L).deviceId("d-1").build());
        assertThrows(IllegalStateException.class, LoginContextUtils::requireUserId);
    }
}
