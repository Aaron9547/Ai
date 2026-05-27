-- 0.1.256：管理端「消息发送」菜单 MESSAGE_CENTER（租户配置分组内；页内 Tab：通道 / 模板 / 发送记录）

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('MESSAGE_CENTER', '消息发送', '/system/message-channels', 43, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

UPDATE sys_admin_menu_item SET title_zh = '消息发送', route_path = '/system/message-channels', updated_at = UTC_TIMESTAMP(3)
WHERE menu_code = 'MESSAGE_CENTER';

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'MESSAGE_CENTER', UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'MESSAGE_CENTER', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';
