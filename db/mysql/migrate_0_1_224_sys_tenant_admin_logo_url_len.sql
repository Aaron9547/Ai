-- 0.1.224：管理端 LOGO 存完整 URL 或 /open/v1 路径时可更长
ALTER TABLE sys_tenant
  MODIFY COLUMN admin_logo_url VARCHAR(2048) NULL COMMENT '管理端侧栏 LOGO（HTTPS 或本服务 /open/v1/admin-brand-logos/...）';
