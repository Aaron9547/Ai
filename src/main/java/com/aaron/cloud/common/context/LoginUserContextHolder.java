package com.aaron.cloud.common.context;

import com.aaron.cloud.common.security.entity.SecUserAccount;
import java.util.Optional;

/** 当前请求线程内的登录用户实体快照；与 {@link TenantContextHolder} 同生命周期。 */
public final class LoginUserContextHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private LoginUserContextHolder() {}

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 由 {@link com.aaron.cloud.gateway.JwtSessionGateFilter} 等在 Filter 链早期写入，供后续 Filter 复用。 */
    public static void bindFromAccount(SecUserAccount account) {
        LoginUser user = LoginUser.fromAccount(account);
        if (user != null) {
            HOLDER.set(user);
        }
    }

    public static LoginUser getOrNull() {
        return HOLDER.get();
    }

    public static Optional<LoginUser> find() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static LoginUser require() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new IllegalStateException("login user context missing");
        }
        return u;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
