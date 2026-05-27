-- 0.1.232：知识库管理端向量检索试跑

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/rag-kbs/*/*/retrieval-test', 'POST', '知识库检索试跑', NULL, 1, 4310, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
