-- 0.1.234：管理端「定时任务」菜单 SCHEDULED_TASKS（租户隔离启用/停用）

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('SCHEDULED_TASKS', '定时任务', '/system/scheduled-tasks', 42, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'SCHEDULED_TASKS', UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'SCHEDULED_TASKS', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';
