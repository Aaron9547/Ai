package com.aaron.cloud.common.security;

import com.aaron.cloud.common.security.entity.LnkTenantAdminMenu;
import com.aaron.cloud.common.security.mapper.LnkTenantAdminMenuMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class LnkTenantAdminMenuRepository {

    private final LnkTenantAdminMenuMapper mapper;

    public List<String> listMenuCodesByTenant(long tenantId) {
        return mapper.selectList(
                        Wrappers.<LnkTenantAdminMenu>lambdaQuery()
                                .eq(LnkTenantAdminMenu::getTenantId, tenantId)
                                .select(LnkTenantAdminMenu::getMenuCode))
                .stream()
                .map(LnkTenantAdminMenu::getMenuCode)
                .toList();
    }

    @Transactional
    public void replaceAllForTenant(long tenantId, List<String> menuCodes) {
        mapper.delete(
                Wrappers.<LnkTenantAdminMenu>lambdaQuery().eq(LnkTenantAdminMenu::getTenantId, tenantId));
        if (menuCodes == null || menuCodes.isEmpty()) {
            return;
        }
        for (String code : menuCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            var row = new LnkTenantAdminMenu();
            row.setTenantId(tenantId);
            row.setMenuCode(code.trim());
            mapper.insert(row);
        }
    }
}
