-- 0.1.94：对话敏感词表 + 限流清单（已上线库按需执行；空库以 schema_v1.sql 为准）

CREATE TABLE IF NOT EXISTS guardrail_sensitive_term (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  pool_type TINYINT NOT NULL COMMENT 'GuardrailSensitivePoolType：0=PLATFORM 全租户强制 1=TENANT 单租户',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT 'PLATFORM 池固定为 0；TENANT 池为 ten id',
  word VARCHAR(191) NOT NULL COMMENT '敏感词文本（trim 后落库）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间（东八区墙钟）',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间（东八区墙钟）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_guardrail_sens_pool_word (pool_type, tenant_id, word),
  KEY idx_guardrail_sens_lookup (pool_type, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话敏感词（平台强制+租户）';

INSERT IGNORE INTO guardrail_sensitive_term (pool_type, tenant_id, word, created_at, updated_at) VALUES
(0, 0, '平台敏感词示例（请改为实际词或删除）', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO gw_api_rate_limit_rule (tenant_id, path_pattern, http_method, requests_per_minute, enabled, remark, created_at, updated_at) VALUES
(NULL, '/api/v1/admin/chat/sensitive-terms', 'GET', 120, 0, '敏感词列表', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/sensitive-terms', 'POST', 30, 0, '新增敏感词', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/sensitive-terms/import', 'POST', 10, 0, '批量导入敏感词', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/sensitive-terms/*', 'DELETE', 30, 0, '删除敏感词', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
