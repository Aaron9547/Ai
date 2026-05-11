package com.aaron.cloud.common.security;

import com.aaron.cloud.common.security.entity.LnkTenantUserAdminMenu;
import com.aaron.cloud.common.security.mapper.LnkTenantUserAdminMenuMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class LnkTenantUserAdminMenuRepository {

    private final LnkTenantUserAdminMenuMapper mapper;

    public List<String> listMenuCodes(long tenantId, long userId) {
        return mapper.selectList(
                        Wrappers.<LnkTenantUserAdminMenu>lambdaQuery()
                                .eq(LnkTenantUserAdminMenu::getTenantId, tenantId)
                                .eq(LnkTenantUserAdminMenu::getUserId, userId)
                                .select(LnkTenantUserAdminMenu::getMenuCode))
                .stream()
                .map(LnkTenantUserAdminMenu::getMenuCode)
                .toList();
    }

    @Transactional
    public void replaceAllForUser(long tenantId, long userId, List<String> menuCodes) {
        mapper.delete(
                Wrappers.<LnkTenantUserAdminMenu>lambdaQuery()
                        .eq(LnkTenantUserAdminMenu::getTenantId, tenantId)
                        .eq(LnkTenantUserAdminMenu::getUserId, userId));
        if (menuCodes == null || menuCodes.isEmpty()) {
            return;
        }
        for (String code : menuCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            var row = new LnkTenantUserAdminMenu();
            row.setTenantId(tenantId);
            row.setUserId(userId);
            row.setMenuCode(code.trim());
            mapper.insert(row);
        }
    }
}
