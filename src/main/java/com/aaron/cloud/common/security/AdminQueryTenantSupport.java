package com.aaron.cloud.common.security;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理端列表/运维查询的租户维度解析：创始人可按参数收窄或查看全量；非创始人固定为 JWT 当前租户且禁止借参数越权。
 */
public final class AdminQueryTenantSupport {

    private AdminQueryTenantSupport() {}

    private static boolean isFounder(TenantMemberRole role) {
        return role != null && role.isFounder();
    }

    /**
     * 访问日志、审计、计量、对话会话等「列表」筛选。
     *
     * @param requestedFilterTenantId 请求中的筛选租户；创始人未传或 {@code null} 表示<strong>不按租户过滤</strong>（全量）；非创始人必须为当前租户或省略。
     * @return {@code null} 表示不按 {@code tenant_id} 过滤；非 {@code null} 表示仅该租户。
     */
    public static Long resolveAdminListTenantFilter(Long requestedFilterTenantId) {
        var snap = TenantContextHolder.require();
        TenantMemberRole role = snap.getMemberRole();
        if (isFounder(role)) {
            return requestedFilterTenantId;
        }
        if (requestedFilterTenantId != null && !requestedFilterTenantId.equals(snap.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅创始人可按租户筛选");
        }
        return snap.getTenantId();
    }

    /**
     * 敏感词「租户扩展池」读写与分页：创始人可指定目标租户数据面；未指定时使用 JWT 工作区租户。
     */
    public static long resolveSensitiveTermsDataTenantId(Long requestedTargetTenantId) {
        var snap = TenantContextHolder.require();
        TenantMemberRole role = snap.getMemberRole();
        if (isFounder(role)) {
            return requestedTargetTenantId != null ? requestedTargetTenantId : snap.getTenantId();
        }
        if (requestedTargetTenantId != null && !requestedTargetTenantId.equals(snap.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作其他租户的扩展词库");
        }
        return snap.getTenantId();
    }
}
