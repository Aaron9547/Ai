package com.aaron.cloud.common.context;

/**
 * 当前请求的租户/用户 id 快照。业务代码优先用 {@link LoginContextUtils} 读取租户与用户实体。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantSnapshot> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void set(TenantSnapshot snapshot) {
        HOLDER.set(snapshot);
    }

    /** 同时写入租户快照与登录用户实体（HTTP Filter 使用）。 */
    public static void set(TenantSnapshot snapshot, LoginUser loginUser) {
        HOLDER.set(snapshot);
        LoginUserContextHolder.set(loginUser);
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
        LoginUserContextHolder.clear();
    }
}
