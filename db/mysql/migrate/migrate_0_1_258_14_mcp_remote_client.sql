-- [0.1.258 #14] MCP 远程客户端：注册表扩展 + 运行时键（与 pom 0.1.258-SNAPSHOT 对齐）
-- 已建库须按 migrate_0_1_258_01 … _14 顺序手工执行；新库 schema_v1.sql 已含表结构时可跳过本文件 DDL。
-- 同补丁全量顺序见 db/mysql/README.md「0.1.258 迁移执行顺序」。

ALTER TABLE mcp_server_registry
  ADD COLUMN transport_kind TINYINT NOT NULL DEFAULT 1 COMMENT 'McpTransportKind：1=STREAMABLE_HTTP' AFTER base_url,
  ADD COLUMN auth_headers_cipher TEXT NULL COMMENT '出站 HTTP 头 AES-GCM 密文 JSON' AFTER transport_kind,
  ADD COLUMN description VARCHAR(512) NULL COMMENT '管理端备注' AFTER auth_headers_cipher,
  ADD COLUMN last_probe_at DATETIME(3) NULL COMMENT '最近探测时间 UTC' AFTER status,
  ADD COLUMN last_probe_ok TINYINT NULL COMMENT '最近探测：0=失败 1=成功' AFTER last_probe_at;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'MCP_CHAT_MAX_TOOL_ROUNDS', '5', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'MCP_CHAT_TOOL_TIMEOUT_SECONDS', '60', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'MCP_CHAT_TOOL_RESULT_MAX_CHARS', '8000', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;
