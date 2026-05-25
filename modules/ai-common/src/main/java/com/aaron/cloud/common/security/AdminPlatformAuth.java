package com.aaron.cloud.common.security;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import org.springframework.security.access.AccessDeniedException;

/** 管理端平台级能力（如全量租户管理）的鉴权入口。 */
public final class AdminPlatformAuth {

    private AdminPlatformAuth() {}

    public static void requireFounder() {
        var snap = TenantContextHolder.require();
        TenantMemberRole r = snap.getMemberRole();
        if (r == null || !r.isFounder()) {
            throw new AccessDeniedException("仅平台创始人可执行此操作");
        }
    }

    public static void requireOwnerOrFounder() {
        var snap = TenantContextHolder.require();
        TenantMemberRole r = snap.getMemberRole();
        if (r == null || (!r.isFounder() && r != TenantMemberRole.OWNER)) {
            throw new AccessDeniedException("仅租户所有者或平台创始人可执行此操作");
        }
    }
}
