-- 0.1.214：管理端「用户画像」菜单码 USER_PROFILES；租户/用户默认绑定该菜单（与既有菜单种子同形）。
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('USER_PROFILES', '用户画像', '/users/profiles', 12, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'USER_PROFILES', UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'USER_PROFILES', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';
