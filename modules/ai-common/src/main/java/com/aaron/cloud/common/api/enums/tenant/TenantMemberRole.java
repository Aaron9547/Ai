package com.aaron.cloud.common.api.enums.tenant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TenantMemberRole {
    /** 平台创始人：可管理全部租户及租户管理 API，可通过请求头切换数据租户。 */
    FOUNDER("FOUNDER"),
    OWNER("OWNER"),
    ADMIN("ADMIN"),
    MEMBER("MEMBER");

    @EnumValue private final String code;

    public static TenantMemberRole fromClaim(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (TenantMemberRole r : values()) {
            if (r.name().equalsIgnoreCase(s) || r.code.equalsIgnoreCase(s)) {
                return r;
            }
        }
        return null;
    }

    public boolean isFounder() {
        return this == FOUNDER;
    }
}
