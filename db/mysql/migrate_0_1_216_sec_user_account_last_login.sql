-- 0.1.216：账号最近登录元数据（时间/IP/粗粒度地区）+ HTTP 日志按租户用户时间索引，支撑管理端在线推断与大屏地域统计。
-- 已上线库执行一次；新库以 schema_v1.sql 为准可跳过或幂等执行（若列已存在会报错，按需注释对应语句）。

ALTER TABLE sec_user_account
  ADD COLUMN last_login_at DATETIME(3) NULL COMMENT '最近一次成功登录时间 UTC' AFTER jwt_seq,
  ADD COLUMN last_login_ip VARCHAR(64) NULL COMMENT '最近一次成功登录 IP' AFTER last_login_at,
  ADD COLUMN last_login_region VARCHAR(128) NULL COMMENT '登录地区粗粒度（如 CF-IPCountry 国家码）' AFTER last_login_ip;

ALTER TABLE sys_http_access_log
  ADD KEY idx_access_tenant_user_time (tenant_id, user_id, created_at);
