package com.aaron.cloud.common.context;

/** 接入方上下文；与 {@link TenantContextHolder} 解耦，仅接入方链路写入。 */
public final class AccessPartyContextHolder {

    private static final ThreadLocal<AccessPartySnapshot> HOLDER = new ThreadLocal<>();

    private AccessPartyContextHolder() {}

    public static void set(AccessPartySnapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static AccessPartySnapshot getOrNull() {
        return HOLDER.get();
    }

    public static AccessPartySnapshot require() {
        var s = HOLDER.get();
        if (s == null || s.getAccessPartyId() == null) {
            throw new IllegalStateException("access party context missing");
        }
        return s;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
