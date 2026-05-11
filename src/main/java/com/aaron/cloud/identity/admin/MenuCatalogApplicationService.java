package com.aaron.cloud.identity.admin;

import com.aaron.cloud.common.api.enums.AdminMenuCode;
import com.aaron.cloud.common.api.enums.ToggleState;
import com.aaron.cloud.common.security.AdminPlatformAuth;
import com.aaron.cloud.common.security.SysAdminMenuItemRepository;
import com.aaron.cloud.common.security.entity.SysAdminMenuItem;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuCatalogApplicationService {

    private final SysAdminMenuItemRepository menuItemRepository;

    public List<SysAdminMenuItem> listAll() {
        return menuItemRepository.listAllOrderBySort();
    }

    @Transactional
    public SysAdminMenuItem create(String menuCode, String titleZh, String routePath, int sortOrder) {
        AdminPlatformAuth.requireFounder();
        AdminMenuCode code = AdminMenuCode.fromStorage(menuCode);
        if (code == null) {
            throw new IllegalArgumentException("unknown menu code");
        }
        if (menuItemRepository.findByMenuCode(code.name()) != null) {
            throw new IllegalStateException("menu item already exists");
        }
        var row = new SysAdminMenuItem();
        row.setMenuCode(code.name());
        row.setTitleZh(titleZh == null || titleZh.isBlank() ? code.name() : titleZh.trim());
        row.setRoutePath(routePath == null || routePath.isBlank() ? null : routePath.trim());
        row.setSortOrder(sortOrder);
        row.setEnabled(ToggleState.ON);
        menuItemRepository.insert(row);
        return Objects.requireNonNull(menuItemRepository.findById(row.getId()));
    }

    @Transactional
    public SysAdminMenuItem update(long id, String titleZh, String routePath, Integer sortOrder, ToggleState enabled) {
        AdminPlatformAuth.requireFounder();
        SysAdminMenuItem row = menuItemRepository.findById(id);
        if (row == null) {
            throw new IllegalArgumentException("menu item not found");
        }
        if (titleZh != null && !titleZh.isBlank()) {
            row.setTitleZh(titleZh.trim());
        }
        if (routePath != null) {
            row.setRoutePath(routePath.isBlank() ? null : routePath.trim());
        }
        if (sortOrder != null) {
            row.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        menuItemRepository.updateById(row);
        return Objects.requireNonNull(menuItemRepository.findById(id));
    }

    @Transactional
    public void delete(long id) {
        AdminPlatformAuth.requireFounder();
        if (menuItemRepository.findById(id) == null) {
            throw new IllegalArgumentException("menu item not found");
        }
        menuItemRepository.deleteById(id);
    }
}
