package com.aaron.cloud.common.context;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder.TenantSnapshot;
import java.util.Optional;

/**
 * 一键读取当前请求的租户与登录用户上下文（优先线程内快照，避免业务层重复查库）。
 *
 * <p>HTTP 请求在 {@link com.aaron.cloud.gateway.TenantContextFilter} 中填充 {@link TenantContextHolder} 与
 * {@link LoginUserContextHolder}；异步任务若仅 {@link TenantContextHolder#set(TenantSnapshot)} 恢复租户快照，则
 * {@link #getUser()} 可能为 {@code null}，但 {@link #getUserId()} 仍可从快照读取。
 */
public final class LoginContextUtils {

    private LoginContextUtils() {}

    public static TenantSnapshot getTenantSnapshot() {
        return TenantContextHolder.getOrNull();
    }

    public static TenantSnapshot requireTenantSnapshot() {
        return TenantContextHolder.require();
    }

    public static Long getTenantId() {
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        return snap == null ? null : snap.getTenantId();
    }

    public static long requireTenantId() {
        return TenantContextHolder.require().getTenantId();
    }

    public static Long getUserId() {
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        return snap == null ? null : snap.getUserId();
    }

    public static long requireUserId() {
        Long uid = getUserId();
        if (uid == null) {
            throw new IllegalStateException("login user id missing in tenant context");
        }
        return uid;
    }

    public static String getDeviceId() {
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        return snap == null ? null : snap.getDeviceId();
    }

    public static TenantMemberRole getMemberRole() {
        TenantSnapshot snap = TenantContextHolder.getOrNull();
        return snap == null ? null : snap.getMemberRole();
    }

    /** 当前登录用户实体；未登录或未在 Filter 中加载时返回 {@code null}。 */
    public static LoginUser getUser() {
        return LoginUserContextHolder.getOrNull();
    }

    public static Optional<LoginUser> findUser() {
        return LoginUserContextHolder.find();
    }

    public static LoginUser requireUser() {
        return LoginUserContextHolder.require();
    }

    public static boolean isLoggedIn() {
        return getUserId() != null;
    }
}
