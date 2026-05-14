-- =============================================================================
-- 网关接口目录 gw_api_endpoint — 全量 INSERT 清单（固定追加档）
-- =============================================================================
-- 用途：管理端「接口与限流」→「接口管理」与限流表单的快捷选择数据源。
--
-- 维护约定（与仓库根目录 .cursorrules §4.1.3「接口目录种子追加」一致）：
--   1. 在 Java 中新增对外 REST（如新增 *RestController、类级路径或 @XxxMapping）后，
--      在本文件**末尾**追加一行 INSERT（保持 INSERT IGNORE 与列顺序一致），勿改历史行。
--   2. path_pattern 使用与运行时 Ant 匹配一致的片段（单段路径或 * / ** 通配）；http_method 大写或 *。
--   3. display_name 用**简短中文**；remark 可空；enabled 默认 1（ON）；sort_order 递增，避免与已占用号冲突。
--   4. 新库全量仍以 schema_v1.sql 为准；本文件供「追加接口」与「对齐已有库」幂等补数据。
--
-- 执行：按需对目标库执行；建议整段或按域分段执行。INSERT IGNORE 跳过 uk_gw_api_endpoint_pm 冲突行。
-- =============================================================================

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
-- ---------- Open：/open/v1/auth ----------
('/open/v1/auth/login', 'POST', '开放登录（jwt-local）', '管理端/C 端共用登录入口', 1, 1000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/auth/register', 'POST', '开放自助注册', '依赖租户开放注册开关', 1, 1010, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- Open：/open/v1/system ----------
('/open/v1/system/me', 'GET', '开放当前上下文 me', '访客可访问', 1, 1020, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- Open：/open/v1/profile（须 JWT）----------
('/open/v1/profile/export', 'GET', '画像与记忆导出', 'jwt-local', 1, 1025, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/profile/data', 'DELETE', '画像与记忆删除', 'jwt-local', 1, 1026, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- Open：/open/v1/chat ----------
('/open/v1/chat/models', 'GET', '对话模型列表', 'C 端选择器', 1, 1030, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/web-search-availability', 'GET', '联网检索是否可用', 'C 端开关', 1, 1035, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations', 'GET', '开放会话列表', NULL, 1, 1040, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations', 'POST', '创建会话', NULL, 1, 1050, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/messages', 'GET', '开放消息列表', NULL, 1, 1060, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/messages', 'POST', '流式发消息 SSE', NULL, 1, 1070, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/messages/*/feedback', 'POST', '助手消息赞踩', NULL, 1, 1080, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/messages/*/retry', 'POST', '重新生成助手 SSE', NULL, 1, 1090, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/conversations/*/attachments', 'POST', '会话附件上传', 'multipart', 1, 1100, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：/api/v1/auth（须 JWT）----------
('/api/v1/auth/admin-context', 'POST', '管理端切换工作区 JWT', 'jwt-local', 1, 2000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：/api/v1/admin/me ----------
('/api/v1/admin/me', 'GET', '管理端当前用户快照', NULL, 1, 2010, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：AdminReadController ----------
('/api/v1/admin/access-logs', 'GET', '访问日志分页', '创始人可 filterTenantId', 1, 2020, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/audit-events', 'GET', '审计事件分页', '创始人可 filterTenantId', 1, 2030, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/metering-events', 'GET', '计量事件分页', '创始人可 filterTenantId', 1, 2040, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/file-objects', 'GET', '文件对象分页', '当前租户', 1, 2050, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/notification-subscriptions', 'GET', '通知订阅分页', '当前租户', 1, 2060, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/eval-runs', 'GET', '评测运行分页', '当前租户', 1, 2070, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：用户 ----------
('/api/v1/admin/users', 'GET', '用户列表', NULL, 1, 2100, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users', 'POST', '创建用户', NULL, 1, 2110, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*', 'GET', '用户详情', NULL, 1, 2120, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*', 'PUT', '更新用户', NULL, 1, 2130, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*', 'DELETE', '删除或禁用用户', NULL, 1, 2140, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*/admin-menus', 'GET', '用户个人菜单', NULL, 1, 2150, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*/admin-menus', 'PUT', '替换用户个人菜单', NULL, 1, 2160, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*/tenant-role', 'PUT', '调整租户成员角色', NULL, 1, 2170, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*/kick-session', 'POST', '踢下线', NULL, 1, 2180, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/users/*/ban', 'POST', '封禁用户', NULL, 1, 2190, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/user-profiles', 'GET', '用户画像列表', NULL, 1, 2192, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/user-profiles/memory-embedding-model', 'GET', '记忆向量化模型配置', NULL, 1, 2193, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/user-profiles/memory-embedding-model', 'PUT', '保存记忆向量化模型', NULL, 1, 2194, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/user-profiles/*', 'GET', '用户画像详情', NULL, 1, 2195, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：租户成员 ----------
('/api/v1/admin/tenant-members', 'GET', '租户成员列表', NULL, 1, 2200, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenant-members', 'POST', '邀请或恢复成员', NULL, 1, 2210, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenant-members/*', 'DELETE', '移出租户成员', '路径为 userId', 1, 2220, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：租户 ----------
('/api/v1/admin/tenants', 'GET', '租户列表', NULL, 1, 2300, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenants', 'POST', '创建租户', NULL, 1, 2310, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenants/*', 'PUT', '更新租户', NULL, 1, 2320, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenants/*/admin-menus', 'GET', '租户后台菜单', NULL, 1, 2330, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenants/*/admin-menus', 'PUT', '替换租户后台菜单', NULL, 1, 2340, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：运行时配置、菜单 ----------
('/api/v1/admin/tenant-runtime-settings', 'GET', '租户运行时配置列表', NULL, 1, 2400, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/tenant-runtime-settings', 'PUT', '更新租户运行时配置', NULL, 1, 2410, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/menu-items', 'GET', '菜单项目录', NULL, 1, 2420, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/menu-items', 'POST', '新增菜单项', NULL, 1, 2430, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/menu-items/*', 'PUT', '更新菜单项', NULL, 1, 2440, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/menu-items/*', 'DELETE', '删除菜单项', NULL, 1, 2450, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：网关限流、接口目录、CORS ----------
('/api/v1/admin/gateway-rate-limits', 'GET', '限流规则分页', NULL, 1, 2500, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-rate-limits', 'POST', '新建限流规则', NULL, 1, 2510, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-rate-limits/*', 'PUT', '更新限流规则', NULL, 1, 2520, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-rate-limits/*', 'DELETE', '删除限流规则', NULL, 1, 2530, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints', 'GET', '接口目录分页', NULL, 1, 2540, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints/picker', 'GET', '接口目录快捷列表', NULL, 1, 2550, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints', 'POST', '新建接口目录项', NULL, 1, 2560, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints/*', 'PUT', '更新接口目录项', NULL, 1, 2570, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/gateway-api-endpoints/*', 'DELETE', '删除接口目录项', NULL, 1, 2580, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/cors-allowed-origins', 'GET', 'CORS 来源列表', NULL, 1, 2590, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/cors-allowed-origins', 'POST', '新增 CORS 来源', NULL, 1, 2600, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/cors-allowed-origins/*', 'PUT', '更新 CORS 来源', NULL, 1, 2610, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/cors-allowed-origins/*', 'DELETE', '删除 CORS 来源', NULL, 1, 2620, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：LLM、MCP、异步任务 ----------
('/api/v1/admin/llm-models/meta', 'GET', 'LLM 模型管理 UI 元数据', 'Tab/表单/列表列与下拉选项', 1, 2680, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/llm-models', 'GET', 'LLM 模型列表', NULL, 1, 2700, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/llm-models', 'POST', '创建 LLM 模型', NULL, 1, 2710, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/llm-models/*', 'PUT', '更新 LLM 模型', NULL, 1, 2720, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/llm-models/*', 'DELETE', '删除 LLM 模型', NULL, 1, 2730, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/mcp-servers', 'GET', 'MCP 服务列表', NULL, 1, 2800, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/mcp-servers', 'POST', '注册 MCP', NULL, 1, 2810, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/mcp-servers/*', 'PUT', '更新 MCP', NULL, 1, 2820, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/mcp-servers/*', 'DELETE', '删除 MCP', NULL, 1, 2830, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/job-tasks', 'GET', '异步任务分页', NULL, 1, 2900, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：管理端对话与敏感词 ----------
('/api/v1/admin/chat/conversations', 'GET', '管理端会话列表', NULL, 1, 3000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/conversations/*/messages', 'GET', '管理端会话消息', NULL, 1, 3010, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/sensitive-terms/platform', 'GET', '敏感词平台池分页', NULL, 1, 3020, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/sensitive-terms/tenant', 'GET', '敏感词租户池分页', NULL, 1, 3030, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/sensitive-terms', 'POST', '新增敏感词', NULL, 1, 3040, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/sensitive-terms/import', 'POST', '批量导入敏感词', NULL, 1, 3050, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/sensitive-terms/*', 'DELETE', '删除敏感词', NULL, 1, 3060, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents', 'GET', '意图列表', NULL, 1, 3070, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents', 'POST', '新增意图', NULL, 1, 3071, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intent-handlers/*/config-schema', 'GET', '意图处理器动态配置元数据', NULL, 1, 3078, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intent-handler-kinds', 'GET', '意图处理器类型下拉（已注册）', NULL, 1, 3079, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents/*', 'PUT', '更新意图', NULL, 1, 3072, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents/*', 'DELETE', '删除意图', NULL, 1, 3073, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents/*/keywords', 'GET', '意图关键词列表', NULL, 1, 3074, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents/*/keywords', 'POST', '新增意图关键词', NULL, 1, 3075, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents/*/keywords/*', 'PUT', '更新意图关键词', NULL, 1, 3076, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/chat/intents/*/keywords/*', 'DELETE', '删除意图关键词', NULL, 1, 3077, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：RAG 知识库（管理端）；首段 * 为 sys_tenant.code，次段 * 为知识库 id ----------
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
('/api/v1/admin/rag-kbs/*/*/documents/upload', 'POST', '上传文档入库', 'multipart', 1, 4230, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：RAG 租户 API ----------
('/api/v1/rag/kbs', 'GET', '租户 RAG 知识库列表', NULL, 1, 4300, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/rag/kbs', 'POST', '租户创建知识库', NULL, 1, 4310, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/rag/kbs/*/index-jobs', 'POST', '租户索引任务', NULL, 1, 4320, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
-- ---------- API：MCP 工具、通知、文件、内部模型 ----------
('/api/v1/mcp/tools/*/invoke', 'POST', 'MCP 工具调用', '路径段为工具名', 1, 5000, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/notifications/webhooks', 'POST', 'Webhook 投递', NULL, 1, 5010, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/file/presign-upload', 'POST', '预签名上传', NULL, 1, 5020, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/internal/model/completion', 'POST', '内部模型补全', '聚合/Feign 非对外主路径', 1, 5030, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
