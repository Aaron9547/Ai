-- 0.1.182：管理端 RAG 知识库路径首段改为租户编码（sys_tenant.code），与 Java 契约一致。
-- 已上线库按需执行；新库以 schema_v1.sql / gw_api_endpoint_catalog_inserts.sql 为准。INSERT IGNORE 与 uk_gw_api_endpoint_pm 幂等。

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
('/api/v1/admin/rag-kbs/*/capabilities', 'GET', 'RAG 能力（Milvus）', NULL, 1, 3990, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*', 'GET', '知识库列表', NULL, 1, 4000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*', 'POST', '创建知识库', NULL, 1, 4010, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*', 'PUT', '更新知识库', NULL, 1, 4020, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*', 'DELETE', '删除知识库', NULL, 1, 4030, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/settings', 'PATCH', '知识库高级设置', NULL, 1, 4040, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/index-jobs', 'POST', '下发索引任务', NULL, 1, 4050, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/url-import-jobs', 'POST', '网页入库任务', NULL, 1, 4060, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/file-ingest-jobs', 'POST', '文件入库任务', NULL, 1, 4070, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/document-categories', 'GET', '文档分类列表', NULL, 1, 4080, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/document-categories', 'POST', '新建文档分类', NULL, 1, 4090, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/document-categories/*', 'PUT', '更新文档分类', NULL, 1, 4100, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/document-categories/*', 'DELETE', '删除文档分类', NULL, 1, 4110, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents', 'GET', '文档列表（旧）', NULL, 1, 4120, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/page', 'GET', '文档分页', NULL, 1, 4130, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/document/*', 'GET', '单文档详情', NULL, 1, 4140, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*', 'PATCH', '更新文档元数据', NULL, 1, 4150, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*/markdown', 'GET', '下载文档 Markdown', NULL, 1, 4160, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*', 'DELETE', '删除文档', NULL, 1, 4170, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*/chunks', 'GET', '文档分片列表', NULL, 1, 4180, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*/chunks', 'POST', '新增分片', NULL, 1, 4190, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*/chunks/*', 'PATCH', '更新分片', NULL, 1, 4200, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*/chunks/*', 'DELETE', '删除分片', NULL, 1, 4210, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/*/chunks/*/merge-with-next', 'POST', '分片与下一块合并', NULL, 1, 4220, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/*/documents/upload', 'POST', '上传文档入库', 'multipart', 1, 4230, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
