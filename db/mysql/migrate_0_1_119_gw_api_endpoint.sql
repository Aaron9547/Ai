-- 0.1.119：网关可管理接口目录 gw_api_endpoint（限流快捷选择）；与 schema_v1.sql 对齐。
CREATE TABLE IF NOT EXISTS gw_api_endpoint (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  path_pattern VARCHAR(512) NOT NULL COMMENT 'Ant 风格路径，与限流规则 path_pattern 语义一致',
  http_method VARCHAR(16) NOT NULL DEFAULT '*' COMMENT '* 或 ANY=任意方法；否则 GET/POST 等大写方法',
  display_name VARCHAR(128) NOT NULL COMMENT '管理端展示名称',
  remark VARCHAR(512) NULL COMMENT '补充说明',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState：0=OFF 不在快捷选择中展示 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gw_api_endpoint_pm (path_pattern(191), http_method),
  KEY idx_gw_api_endpoint_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP 接口目录（限流快捷选择）';

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
('/open/v1/auth/login', 'POST', '开放登录', '访客/用户登录', 1, 10, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/messages', 'POST', '开放对话流式', 'SSE 发消息', 1, 20, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/me', 'GET', '管理端当前用户', NULL, 1, 30, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users', 'GET', '用户列表', NULL, 1, 40, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users', 'POST', '创建用户', NULL, 1, 41, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenants', 'GET', '租户列表', NULL, 1, 50, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-rate-limits', 'GET', '限流规则分页', NULL, 1, 60, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-rate-limits', 'POST', '新建限流规则', NULL, 1, 61, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints', 'GET', '接口目录分页', NULL, 1, 62, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints/picker', 'GET', '接口目录快捷列表', NULL, 1, 63, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints', 'POST', '新建接口目录项', NULL, 1, 64, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/cors-allowed-origins', 'GET', 'CORS 来源列表', NULL, 1, 70, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/**', 'GET', 'RAG 知识库（读）', '含子路径', 1, 80, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/**', 'POST', 'RAG 知识库（写）', '含子路径', 1, 81, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/llm-models/**', '*', '模型管理', '通配子路径', 1, 90, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/access-logs', 'GET', '访问日志', NULL, 1, 100, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/audit-events', 'GET', '审计事件', NULL, 1, 110, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/job-tasks', 'GET', '异步任务', NULL, 1, 120, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO gw_api_rate_limit_rule (tenant_id, path_pattern, http_method, requests_per_minute, enabled, remark, created_at, updated_at) VALUES
(NULL, '/api/v1/admin/gateway-api-endpoints', 'GET', 120, 0, '接口目录分页/快捷', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/gateway-api-endpoints/picker', 'GET', 120, 0, '接口目录 picker', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/gateway-api-endpoints', 'POST', 30, 0, '新建接口目录项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/gateway-api-endpoints/*', 'PUT', 30, 0, '更新接口目录项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/gateway-api-endpoints/*', 'DELETE', 20, 0, '删除接口目录项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
