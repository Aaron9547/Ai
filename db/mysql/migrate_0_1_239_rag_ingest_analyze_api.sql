-- 0.1.239：上传入库前子母分片推荐分析 API

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
  ('/api/v1/admin/rag-kbs/*/*/ingest/analyze', 'POST', '上传文档入库前分析（子母分片推荐）', NULL, 1, 4262, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  ('/api/v1/admin/rag-kbs/*/*/ingest/analyze-upload', 'POST', '上传文件入库前分析（子母分片推荐）', NULL, 1, 4263, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
