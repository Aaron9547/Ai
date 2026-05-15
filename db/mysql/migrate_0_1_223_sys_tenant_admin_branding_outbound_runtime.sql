-- 0.1.223：管理端租户壳（LOGO / 标题 / 页脚）列；出站韧性租户 JSON 默认行（独立配置页维护）。
ALTER TABLE sys_tenant
  ADD COLUMN admin_logo_url VARCHAR(2048) NULL COMMENT '管理端侧栏 LOGO（HTTPS 或 /open/v1/admin-brand-logos/...；空则占位符）' AFTER name,
  ADD COLUMN admin_portal_title VARCHAR(255) NULL COMMENT '管理端展示标题（空则回退 name）' AFTER admin_logo_url,
  ADD COLUMN admin_footer_text VARCHAR(2000) NULL COMMENT '管理端页脚纯文本（空则隐藏或默认短文案）' AFTER admin_portal_title;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'OUTBOUND_RESILIENCE_JSON', '{}', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;
