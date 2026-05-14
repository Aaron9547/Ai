-- 0.1.215：管理端「数据概览」大屏菜单码 DASHBOARD；租户/默认 admin 用户绑定（与同系列迁移一致）。
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('DASHBOARD', '数据概览', '/dashboard', 5, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'DASHBOARD', UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'DASHBOARD', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';
