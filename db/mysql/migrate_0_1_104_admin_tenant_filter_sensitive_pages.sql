-- 0.1.104：管理端按租户筛选列表 + 敏感词分页 GET；已上线库按需执行；空库以 schema_v1.sql 为准

INSERT IGNORE INTO gw_api_rate_limit_rule (tenant_id, path_pattern, http_method, requests_per_minute, enabled, remark, created_at, updated_at) VALUES
(NULL, '/api/v1/admin/chat/sensitive-terms/platform', 'GET', 120, 0, '敏感词平台池分页', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/sensitive-terms/tenant', 'GET', 120, 0, '敏感词租户池分页', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
