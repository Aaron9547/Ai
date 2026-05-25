-- 接入方 / 模块 / 授权 / 审计（0.1.251-SNAPSHOT）；已建库须手工执行

ALTER TABLE gw_api_endpoint
  ADD COLUMN module_id BIGINT NULL COMMENT '主归属模块 gw_api_module.id' AFTER sort_order,
  ADD COLUMN global_rpm_cap INT NOT NULL DEFAULT 0 COMMENT '接口全局 RPM 池上限；0=不限制全局池' AFTER module_id,
  ADD COLUMN interface_kind VARCHAR(32) NOT NULL DEFAULT 'OTHER' COMMENT 'GwApiInterfaceKind' AFTER global_rpm_cap,
  ADD KEY idx_gw_api_endpoint_module (module_id);

CREATE TABLE IF NOT EXISTS gw_api_module (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  code VARCHAR(64) NOT NULL COMMENT '模块编码 MODEL/ABILITY/KNOWLEDGE 等',
  display_name VARCHAR(128) NOT NULL COMMENT '展示名',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState',
  remark VARCHAR(512) NULL COMMENT '备注',
  created_at DATETIME(3) NOT NULL COMMENT 'UTC',
  updated_at DATETIME(3) NOT NULL COMMENT 'UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gw_api_module_code (code),
  KEY idx_gw_api_module_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关接口模块';

CREATE TABLE IF NOT EXISTS lnk_gw_module_endpoint (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  module_id BIGINT NOT NULL COMMENT 'gw_api_module.id',
  endpoint_id BIGINT NOT NULL COMMENT 'gw_api_endpoint.id',
  created_at DATETIME(3) NOT NULL COMMENT 'UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lnk_gw_module_endpoint (module_id, endpoint_id),
  KEY idx_lnk_gw_module_ep_endpoint (endpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块与接口关联';

CREATE TABLE IF NOT EXISTS gw_access_party (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '归属租户',
  app_id VARCHAR(64) NOT NULL COMMENT '对外 AppId，全局唯一',
  secret_cipher MEDIUMTEXT NOT NULL COMMENT 'Secret AES-GCM 密文',
  display_name VARCHAR(128) NOT NULL COMMENT '接入方名称',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState ON=正常',
  total_rpm_cap INT NOT NULL DEFAULT 0 COMMENT '总授权 RPM 上限',
  remark VARCHAR(512) NULL COMMENT '备注',
  last_rotated_at DATETIME(3) NULL COMMENT 'Secret 最近轮换时间 UTC',
  created_at DATETIME(3) NOT NULL COMMENT 'UTC',
  updated_at DATETIME(3) NOT NULL COMMENT 'UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gw_access_party_app_id (app_id),
  KEY idx_gw_access_party_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部接入方';

CREATE TABLE IF NOT EXISTS gw_access_party_grant (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  access_party_id BIGINT NOT NULL COMMENT 'gw_access_party.id',
  endpoint_id BIGINT NOT NULL COMMENT 'gw_api_endpoint.id',
  module_id BIGINT NULL COMMENT '授权向导模块快照',
  granted_rpm INT NOT NULL DEFAULT 0 COMMENT '单接口 RPM 配额；0=禁止调用',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState',
  created_at DATETIME(3) NOT NULL COMMENT 'UTC',
  updated_at DATETIME(3) NOT NULL COMMENT 'UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gw_ap_grant_party_ep (access_party_id, endpoint_id),
  KEY idx_gw_ap_grant_endpoint (endpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接入方接口授权与配额';

CREATE TABLE IF NOT EXISTS gw_access_party_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户',
  access_party_id BIGINT NOT NULL COMMENT '接入方',
  endpoint_id BIGINT NULL COMMENT '匹配到的接口目录 id',
  method VARCHAR(16) NOT NULL COMMENT 'HTTP 方法',
  path_pattern VARCHAR(512) NOT NULL COMMENT '请求路径',
  http_status INT NOT NULL COMMENT 'HTTP 状态',
  duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  client_ip VARCHAR(64) NULL COMMENT '客户端 IP',
  tokens_consumed BIGINT NOT NULL DEFAULT 0 COMMENT 'LLM tokens',
  interface_kind VARCHAR(32) NULL COMMENT 'GwApiInterfaceKind',
  error_code VARCHAR(64) NULL COMMENT '业务错误码',
  trace_id VARCHAR(64) NULL COMMENT '链路 id',
  created_at DATETIME(3) NOT NULL COMMENT 'UTC',
  PRIMARY KEY (id),
  KEY idx_gw_ap_log_tenant_party_time (tenant_id, access_party_id, created_at),
  KEY idx_gw_ap_log_endpoint_time (endpoint_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接入方调用审计';

INSERT IGNORE INTO gw_api_module (code, display_name, sort_order, enabled, remark, created_at, updated_at) VALUES
('MODEL', '模型接口', 10, 1, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('ABILITY', '能力接口', 20, 1, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('KNOWLEDGE', '知识接口', 30, 1, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
