package com.aaron.cloud.identity.admin;

import com.aaron.cloud.common.api.enums.gateway.AdminMenuCode;
import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.LnkTenantAdminMenuRepository;
import com.aaron.cloud.common.security.LnkTenantUserAdminMenuRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMenuAuthorizationService {

    private final LnkTenantAdminMenuRepository tenantAdminMenuRepository;
    private final LnkTenantUserAdminMenuRepository userAdminMenuRepository;

    public Set<String> allowedMenuCodesForSnapshot() {
        var snap = TenantContextHolder.require();
        TenantMemberRole role = snap.getMemberRole();
        if (role == null) {
            return Set.of();
        }
        // 平台创始人：始终拥有全部管理端菜单（与 lnk_tenant_admin_menu 是否落库无关，不可被租户表收窄）
        if (role.isFounder()) {
            return AdminMenuCode.all().stream().map(AdminMenuCode::name).collect(java.util.stream.Collectors.toSet());
        }
        long tid = snap.getTenantId();
        Long uid = snap.getUserId();
        if (uid == null) {
            return Set.of();
        }
        if (role == TenantMemberRole.OWNER) {
            return ownerMenuCodes(tid);
        }
        if (role == TenantMemberRole.ADMIN) {
            return adminMenuCodes(tid, uid);
        }
        return Set.of();
    }

    private Set<String> ownerMenuCodes(long tenantId) {
        List<String> rows = tenantAdminMenuRepository.listMenuCodesByTenant(tenantId);
        if (rows.isEmpty()) {
            return AdminMenuCode.all().stream().map(AdminMenuCode::name).collect(java.util.stream.Collectors.toSet());
        }
        return new LinkedHashSet<>(rows);
    }

    /**
     * 管理员个人菜单与租户菜单求交；若尚未配置个人菜单（无行），则视为继承租户侧菜单全集，
     * 避免上线后既有 ADMIN 账号在 {@code lnk_tenant_user_admin_menu} 为空时整站管理端 403。
     */
    private Set<String> adminMenuCodes(long tenantId, long userId) {
        Set<String> tenantSet = ownerMenuCodes(tenantId);
        List<String> userRows = userAdminMenuRepository.listMenuCodes(tenantId, userId);
        if (userRows.isEmpty()) {
            return new LinkedHashSet<>(tenantSet);
        }
        Set<String> out = new LinkedHashSet<>();
        for (String c : userRows) {
            if (tenantSet.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    public boolean isHttpPathAllowed(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if (requestUri.startsWith("/api/v1/admin/me")) {
            return true;
        }
        if (requestUri.contains("/admin-menus")) {
            return true;
        }
        AdminMenuCode need = AdminHttpMenuRoutes.resolve(requestUri);
        if (need == null) {
            return false;
        }
        return allowedMenuCodesForSnapshot().contains(need.name());
    }
}
