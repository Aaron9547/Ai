package com.aaron.cloud.identity.admin;

import com.aaron.cloud.common.api.enums.gateway.AdminMenuCode;
import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.AdminPlatformAuth;
import com.aaron.cloud.common.security.LnkTenantAdminMenuRepository;
import com.aaron.cloud.common.security.LnkTenantUserAdminMenuRepository;
import com.aaron.cloud.common.security.SysTenantMemberRepository;
import com.aaron.cloud.common.security.entity.SysTenantMember;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdminMenuApplicationService {

    private final SysTenantMemberRepository tenantMemberRepository;
    private final LnkTenantAdminMenuRepository tenantAdminMenuRepository;
    private final LnkTenantUserAdminMenuRepository userAdminMenuRepository;

    public List<String> listForUser(long targetUserId) {
        AdminPlatformAuth.requireOwnerOrFounder();
        long tid = TenantContextHolder.require().getTenantId();
        assertCanConfigureTarget(tid, targetUserId);
        return userAdminMenuRepository.listMenuCodes(tid, targetUserId);
    }

    @Transactional
    public void replaceForUser(long targetUserId, List<String> menuCodes) {
        AdminPlatformAuth.requireOwnerOrFounder();
        long tid = TenantContextHolder.require().getTenantId();
        assertCanConfigureTarget(tid, targetUserId);
        Set<String> tenantAllowed = tenantAllowedMenuCodes(tid);
        if (menuCodes != null) {
            for (String raw : menuCodes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String c = raw.trim();
                if (AdminMenuCode.fromStorage(c) == null) {
                    throw new IllegalArgumentException("unknown menu code: " + c);
                }
                if (!tenantAllowed.contains(c)) {
                    throw new IllegalArgumentException("menu not enabled for tenant: " + c);
                }
            }
        }
        userAdminMenuRepository.replaceAllForUser(tid, targetUserId, menuCodes);
    }

    private void assertCanConfigureTarget(long tenantId, long targetUserId) {
        SysTenantMember target =
                tenantMemberRepository
                        .find(tenantId, targetUserId)
                        .orElseThrow(() -> new IllegalArgumentException("user not in tenant"));
        if (target.getRoleCode() != TenantMemberRole.ADMIN) {
            throw new AccessDeniedException("仅可为租户管理员配置后台菜单");
        }
    }

    private Set<String> tenantAllowedMenuCodes(long tenantId) {
        List<String> rows = tenantAdminMenuRepository.listMenuCodesByTenant(tenantId);
        if (rows.isEmpty()) {
            return AdminMenuCode.all().stream()
                    .map(AdminMenuCode::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return new LinkedHashSet<>(rows);
    }
}
