-- 平台系统参数管理端菜单（AdminMenuCode.PLATFORM_SETTINGS；pom 补丁 258）
INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('PLATFORM_SETTINGS', '平台系统参数', '/system/platform-settings', 26, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'PLATFORM_SETTINGS', UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'PLATFORM_SETTINGS', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';
