package com.aaron.cloud.identity.tenant;

import com.aaron.cloud.common.api.enums.gateway.AdminMenuCode;
import com.aaron.cloud.common.api.enums.tenant.TenantStatus;
import com.aaron.cloud.common.security.AdminPlatformAuth;
import com.aaron.cloud.common.security.LnkTenantAdminMenuRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.tenant.entity.SysTenant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantAdminApplicationService {

    private final SysTenantRepository tenantRepository;
    private final LnkTenantAdminMenuRepository tenantAdminMenuRepository;

    public List<SysTenant> listAll() {
        AdminPlatformAuth.requireFounder();
        return tenantRepository.listAllOrderById();
    }

    public SysTenant create(String code, String name) {
        AdminPlatformAuth.requireFounder();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("租户编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("租户名称不能为空");
        }
        String c = code.trim();
        if (tenantRepository.findByCode(c).isPresent()) {
            throw new IllegalStateException("租户编码已存在");
        }
        var t = new SysTenant();
        t.setCode(c);
        t.setName(name.trim());
        t.setStatus(TenantStatus.ACTIVE);
        tenantRepository.insert(t);
        return tenantRepository.findById(t.getId()).orElse(t);
    }

    public SysTenant update(long id, String name, TenantStatus status) {
        AdminPlatformAuth.requireFounder();
        var t =
                tenantRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        if (name != null && !name.isBlank()) {
            t.setName(name.trim());
        }
        if (status != null) {
            t.setStatus(status);
        }
        tenantRepository.updateById(t);
        return tenantRepository.findById(id).orElseThrow();
    }

    public List<String> listTenantAdminMenus(long tenantId) {
        AdminPlatformAuth.requireFounder();
        tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        return tenantAdminMenuRepository.listMenuCodesByTenant(tenantId);
    }

    @Transactional
    public void replaceTenantAdminMenus(long tenantId, List<String> menuCodes) {
        AdminPlatformAuth.requireFounder();
        tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        if (menuCodes != null) {
            for (String raw : menuCodes) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                if (AdminMenuCode.fromStorage(raw.trim()) == null) {
                    throw new IllegalArgumentException("unknown menu code: " + raw);
                }
            }
        }
        tenantAdminMenuRepository.replaceAllForTenant(tenantId, menuCodes);
    }
}
