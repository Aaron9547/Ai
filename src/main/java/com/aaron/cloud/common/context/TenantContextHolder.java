package com.aaron.cloud.common.context;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import lombok.Builder;
import lombok.Value;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantSnapshot> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(TenantSnapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static TenantSnapshot require() {
        var s = HOLDER.get();
        if (s == null || s.getTenantId() == null) {
            throw new IllegalStateException("tenant context missing");
        }
        return s;
    }

    public static TenantSnapshot getOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Value
    @Builder
    public static class TenantSnapshot {
        Long tenantId;
        Long userId;
        String deviceId;
        /** 来自 JWT {@code tmr}，开放接口可能为 null */
        @Builder.Default
        TenantMemberRole memberRole = null;
    }
}
