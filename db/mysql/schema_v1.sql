-- =============================================================================
-- Ai 中台 MySQL 唯一基线脚本（DDL + 幂等种子 + 可选清单）
-- =============================================================================
-- 用途：新环境请整文件执行一次（MySQL 5.7+ / 8.x；MariaDB 10.x 兼容）。
-- 约定：DATETIME 业务墙钟默认 Asia/Shanghai（与 CommonMetaObjectHandler / spring.jackson.time-zone 一致）；禁止物理 FOREIGN KEY；多对多/一对多用 lnk_*；原 JSON 语义列用 LONGTEXT 存 UTF-8 JSON。
-- 历史说明：原分散的 migrate_*.sql / seed_*.sql 已合并入本文件；已上线旧库请勿重复执行整文件，请用结构对比工具增量对齐。
-- =============================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 租户（存量表名 sys_tenant；新表优先 ten_* 见 .cursorrules）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  code VARCHAR(64) NOT NULL COMMENT '租户唯一编码（英文），用于 HTTP 头 X-Tenant-Code 等',
  name VARCHAR(255) NOT NULL COMMENT '租户展示名称',
  admin_logo_url VARCHAR(2048) NULL COMMENT '管理端侧栏 LOGO（HTTPS 或本服务 /open/v1/admin-brand-logos/...；空则占位符）',
  admin_portal_title VARCHAR(255) NULL COMMENT '管理端展示标题（空则回退 name）',
  admin_footer_text VARCHAR(2000) NULL COMMENT '管理端页脚纯文本（空则隐藏或默认短文案）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'TenantStatus：0=DISABLED 停用 1=ACTIVE 启用',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_tenant_code (code),
  KEY idx_sys_tenant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户主数据';

-- ---------------------------------------------------------------------------
-- 对话：会话
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_conversation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NULL COMMENT '已登录用户 ID；访客会话为 NULL',
  device_id VARCHAR(64) NULL COMMENT '访客设备码 X-Device-Id；与 tenant_id 组成匿名主体',
  title VARCHAR(512) NOT NULL DEFAULT '' COMMENT '会话标题（可由首条消息生成）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'ConversationRecordStatus：0=ARCHIVED 1=ACTIVE',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '最后更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_chat_conv_tenant (tenant_id),
  KEY idx_chat_conv_device (tenant_id, device_id),
  KEY idx_chat_conv_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话头';

-- ---------------------------------------------------------------------------
-- 对话：消息（内容行与 lnk 表关联顺序）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  role TINYINT NOT NULL COMMENT 'ChatMessageRole：0=SYSTEM 1=USER 2=ASSISTANT 3=TOOL',
  content MEDIUMTEXT NOT NULL COMMENT '消息正文（可含 Markdown 等）',
  meta_json LONGTEXT NULL COMMENT '扩展元数据 JSON 文本（模型名、token、附件引用等）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  KEY idx_chat_msg_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息行';

CREATE TABLE IF NOT EXISTS lnk_chat_conversation_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT NOT NULL COMMENT 'chat_conversation.id',
  message_id BIGINT NOT NULL COMMENT 'chat_message.id',
  seq INT NOT NULL DEFAULT 0 COMMENT '会话内序号，用于排序',
  created_at DATETIME(3) NOT NULL COMMENT '关联创建时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lnk_chat_conv_msg (conversation_id, message_id),
  KEY idx_lnk_chat_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话与消息多对多（顺序）';

-- ---------------------------------------------------------------------------
-- 对话：可配置意图（关键词触发 + 处理器枚举；扩展新意图时增表行与后端 Handler）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_intent_definition (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  code VARCHAR(64) NOT NULL COMMENT '租户内唯一意图编码（英文 snake）',
  display_name VARCHAR(128) NOT NULL COMMENT '展示名',
  description VARCHAR(512) NULL COMMENT '说明',
  handler_kind TINYINT NOT NULL DEFAULT 0 COMMENT 'ChatIntentHandlerKind：0=TRAVEL_REIMBURSEMENT',
  enabled TINYINT NOT NULL DEFAULT 0 COMMENT '0=OFF 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同租户多条意图时匹配优先级（大者优先尝试）',
  extra_config_json LONGTEXT NULL COMMENT '处理器扩展 JSON（如 Coze 域名/工作流 id/密钥句柄等，勿存生产明文密钥）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_intent_def_tenant_code (tenant_id, code),
  KEY idx_chat_intent_def_tenant_en (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话意图定义';

CREATE TABLE IF NOT EXISTS chat_intent_keyword (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  intent_id BIGINT NOT NULL COMMENT 'chat_intent_definition.id',
  phrase VARCHAR(128) NOT NULL COMMENT '触发短语（子串包含匹配，trim 后落库；≤128 字符以兼容 utf8mb4 下 InnoDB 767 字节索引上限）',
  keyword_kind TINYINT NOT NULL DEFAULT 0 COMMENT 'ChatIntentKeywordKind：0=TRIGGER 首轮触发 1=PLAN_CONTINUE 行程阶段续办',
  target_round VARCHAR(32) NULL COMMENT '可选；多轮流处理器轮次名（如 DOC、PLAN），为空时由处理器按 keyword_kind 推断',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0=OFF 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
  hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '配置关键词子串命中并进入意图 SSE 的累计次数（仅 intentHitKeywordId 对应行累加）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_intent_kw_intent_phrase (intent_id, phrase),
  KEY idx_chat_intent_kw_tenant_intent (tenant_id, intent_id),
  KEY idx_chat_intent_kw_tenant_phrase (tenant_id, phrase(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意图触发关键词';

-- ---------------------------------------------------------------------------
-- RAG：知识库 / 文档 / 分块及关联
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rag_knowledge_base (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  name VARCHAR(255) NOT NULL COMMENT '知识库名称',
  default_chunk_strategy SMALLINT NOT NULL DEFAULT 1 COMMENT 'RagChunkStrategy：0=NONE 1=FIXED_CHAR 2=SEMANTIC 3=SLIDING_WINDOW 99=CUSTOM',
  chunk_fixed_chars INT NOT NULL DEFAULT 800 COMMENT '固定字数分片目标长度',
  chunk_slide_overlap INT NOT NULL DEFAULT 120 COMMENT '滑动窗口重叠字符数',
  assigned_llm_model_id BIGINT NULL COMMENT '绑定的租户可配模型 llm_model.id（对话侧 LANGUAGE）',
  assigned_embedding_model_id BIGINT NULL COMMENT '绑定的嵌入模型 llm_model.id（须 VECTOR；路径由 llm_model.integration_backend 决定）',
  chat_retrieval_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState：是否在对话编排中纳入本知识库检索（0=OFF 1=ON）',
  chat_vector_min_cosine_score DECIMAL(5,4) NOT NULL DEFAULT 0.6500 COMMENT '对话侧 Milvus COSINE 分数下限（按知识库配置，多库各自独立）；0=关闭该库向量阈值过滤',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_rag_kb_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 知识库';

CREATE TABLE IF NOT EXISTS rag_kb_document_category (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  kb_id BIGINT NOT NULL COMMENT 'rag_knowledge_base.id',
  name VARCHAR(128) NOT NULL COMMENT '分类名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_rag_kb_doc_cat_kb (kb_id, sort_order),
  KEY idx_rag_kb_doc_cat_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档分类';

CREATE TABLE IF NOT EXISTS rag_document (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0=有效 1=逻辑删除',
  source_type VARCHAR(32) NOT NULL DEFAULT 'FILE' COMMENT 'RagDocumentSourceType',
  title VARCHAR(512) NOT NULL DEFAULT '' COMMENT '文档标题',
  source_uri VARCHAR(1024) NULL COMMENT '来源 URL 或路径',
  original_filename VARCHAR(512) NULL COMMENT '上传文件名',
  md_content MEDIUMTEXT NULL COMMENT '规范化 Markdown 正文',
  content_length BIGINT NOT NULL DEFAULT 0 COMMENT '正文长度',
  category_id BIGINT NULL COMMENT 'rag_kb_document_category.id',
  display_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'RagDocumentDisplayStatus',
  applicable_scope VARCHAR(512) NULL COMMENT '适用范围说明',
  uploaded_by_user_id BIGINT NULL COMMENT '上传人 sec_user_account.id',
  hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '对话 RAG 召回命中累计次数（按分片命中计次汇总到文档）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  deleted_at DATETIME(3) NULL COMMENT '逻辑删除时间 UTC',
  PRIMARY KEY (id),
  KEY idx_rag_doc_tenant (tenant_id),
  KEY idx_rag_doc_tenant_deleted (tenant_id, deleted),
  KEY idx_rag_doc_category (tenant_id, category_id),
  KEY idx_rag_doc_display_status (tenant_id, display_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 文档';

CREATE TABLE IF NOT EXISTS rag_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0=有效 1=逻辑删除',
  content TEXT NOT NULL COMMENT '分块纯文本（用于检索/展示）',
  embedding_ref VARCHAR(128) NULL COMMENT '向量库侧引用 ID 或 collection+id',
  retrieval_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'RagChunkRetrievalEnabled：1=ENABLED 参与检索，0=DISABLED',
  hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '对话 RAG 召回命中该分片的累计次数',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_rag_chunk_tenant (tenant_id),
  KEY idx_rag_chunk_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 文本分块';

CREATE TABLE IF NOT EXISTS lnk_rag_kb_document (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  kb_id BIGINT NOT NULL COMMENT 'rag_knowledge_base.id',
  document_id BIGINT NOT NULL COMMENT 'rag_document.id',
  created_at DATETIME(3) NOT NULL COMMENT '关联创建时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lnk_rag_kb_doc (kb_id, document_id),
  KEY idx_lnk_rag_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库与文档多对多';

CREATE TABLE IF NOT EXISTS lnk_rag_document_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  document_id BIGINT NOT NULL COMMENT 'rag_document.id',
  chunk_id BIGINT NOT NULL COMMENT 'rag_chunk.id',
  seq INT NOT NULL DEFAULT 0 COMMENT '文档内分块顺序',
  created_at DATETIME(3) NOT NULL COMMENT '关联创建时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lnk_rag_doc_chunk (document_id, chunk_id),
  KEY idx_lnk_rag_doc (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档与分块多对多';

-- ---------------------------------------------------------------------------
-- 异步任务
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS job_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NULL COMMENT '触发用户（可空）',
  device_id VARCHAR(64) NULL COMMENT '触发设备码（可空）',
  task_type VARCHAR(64) NOT NULL COMMENT 'JobTaskType 等业务类型码',
  status TINYINT NOT NULL DEFAULT 0 COMMENT 'JobTaskStatus：0=PENDING 1=RUNNING 2=SUCCEEDED 3=FAILED 4=CANCELLED',
  payload_json LONGTEXT NULL COMMENT '任务入参 JSON',
  rag_kb_id BIGINT NULL COMMENT 'RAG 知识库 id（与 payload_json.kbId 冗余；管理端按库筛选，避免依赖 JSON 函数）',
  result_json LONGTEXT NULL COMMENT '任务结果或错误 JSON',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_job_task_tenant (tenant_id, status),
  KEY idx_job_task_tenant_rag_kb (tenant_id, rag_kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步任务';

-- ---------------------------------------------------------------------------
-- 平台横切：HTTP 访问日志 / 审计
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_http_access_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NULL COMMENT '租户 ID，开放接口可能为空或由网关解析',
  user_id BIGINT NULL COMMENT '已登录用户 ID',
  device_id VARCHAR(64) NULL COMMENT '设备码',
  method VARCHAR(16) NOT NULL COMMENT 'HTTP 方法',
  path_pattern VARCHAR(512) NOT NULL COMMENT '路由模板化路径（脱敏后）',
  http_status INT NOT NULL COMMENT 'HTTP 状态码',
  duration_ms BIGINT NOT NULL COMMENT '耗时毫秒',
  trace_id VARCHAR(64) NULL COMMENT '链路 trace / request id',
  user_agent VARCHAR(512) NULL COMMENT 'User-Agent（可截断）',
  client_ip VARCHAR(64) NULL COMMENT '客户端 IP',
  created_at DATETIME(3) NOT NULL COMMENT '请求完成时间 UTC',
  PRIMARY KEY (id),
  KEY idx_access_tenant_time (tenant_id, created_at),
  KEY idx_access_tenant_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='南北向 HTTP 访问日志（非业务对话内容）';

CREATE TABLE IF NOT EXISTS sys_audit_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  actor_type VARCHAR(32) NOT NULL COMMENT '操作者类型（user/system/api 等）',
  actor_id VARCHAR(128) NOT NULL COMMENT '操作者标识（用户 ID 或账号）',
  action VARCHAR(128) NOT NULL COMMENT '动作编码（如 CONFIG_UPDATE）',
  resource_type VARCHAR(128) NULL COMMENT '资源类型',
  resource_id VARCHAR(128) NULL COMMENT '资源 ID',
  detail_json LONGTEXT NULL COMMENT '详情 JSON（脱敏）',
  created_at DATETIME(3) NOT NULL COMMENT '事件时间 UTC',
  PRIMARY KEY (id),
  KEY idx_audit_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务审计事件 append-only';

-- ---------------------------------------------------------------------------
-- 计量
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS metering_usage_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NULL COMMENT '用户 ID',
  device_id VARCHAR(64) NULL COMMENT '设备码',
  meter_type VARCHAR(64) NOT NULL COMMENT 'MeteringMeterType 等计量类型',
  quantity DECIMAL(18,4) NOT NULL COMMENT '用量数量',
  unit VARCHAR(32) NOT NULL COMMENT '单位（如 token、次）',
  ref_json LONGTEXT NULL COMMENT '关联引用 JSON（模型别名、会话 id 等）',
  created_at DATETIME(3) NOT NULL COMMENT '计量发生时间 UTC',
  PRIMARY KEY (id),
  KEY idx_meter_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用量计量流水';

-- ---------------------------------------------------------------------------
-- MCP 注册
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mcp_server_registry (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键（注册记录归属）',
  name VARCHAR(128) NOT NULL COMMENT 'MCP 服务逻辑名',
  base_url VARCHAR(1024) NOT NULL COMMENT 'MCP Server Base URL',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'McpServerStatus：0=DISABLED 1=ACTIVE',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_mcp_srv_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP Server 注册';

-- ---------------------------------------------------------------------------
-- 护栏规则
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS guardrail_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  name VARCHAR(128) NOT NULL COMMENT '规则名称',
  pattern VARCHAR(512) NOT NULL COMMENT '匹配模式（关键词/正则等）',
  action_type TINYINT NOT NULL DEFAULT 1 COMMENT 'GuardrailActionType：0=LOG_ONLY 1=BLOCK 2=MASK',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_guard_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轻量护栏规则';

-- ---------------------------------------------------------------------------
-- 敏感词（平台强制池 + 租户扩展池；对话护栏子串命中）
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 文件元数据
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_object_meta (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  bucket VARCHAR(128) NOT NULL COMMENT '对象存储桶名',
  object_key VARCHAR(1024) NOT NULL COMMENT '对象键',
  size_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小字节',
  content_type VARCHAR(128) NULL COMMENT 'MIME 类型',
  scan_status TINYINT NOT NULL DEFAULT 0 COMMENT 'FileScanStatus：0=UNKNOWN 1=PENDING 2=CLEAN 3=INFECTED',
  created_at DATETIME(3) NOT NULL COMMENT '上传/登记时间 UTC',
  PRIMARY KEY (id),
  KEY idx_file_meta_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对象存储文件元数据';

-- ---------------------------------------------------------------------------
-- 通知 Webhook
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_webhook_subscription (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  target_url VARCHAR(1024) NOT NULL COMMENT '回调 URL',
  secret_handle VARCHAR(128) NULL COMMENT '签名校验密钥句柄（非明文）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'SubscriptionStatus：0=DISABLED 1=ACTIVE',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_notify_sub_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 订阅';

CREATE TABLE IF NOT EXISTS notification_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subscription_id BIGINT NOT NULL COMMENT 'notification_webhook_subscription.id',
  http_status INT NULL COMMENT '投递时 HTTP 状态码',
  result_json LONGTEXT NULL COMMENT '响应体或错误摘要 JSON',
  created_at DATETIME(3) NOT NULL COMMENT '投递时间 UTC',
  PRIMARY KEY (id),
  KEY idx_notify_log_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 投递日志';

-- ---------------------------------------------------------------------------
-- 评测流水线运行
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eval_pipeline_run (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  pipeline_code VARCHAR(64) NOT NULL COMMENT '流水线编码',
  status TINYINT NOT NULL DEFAULT 0 COMMENT 'EvalRunStatus：0=QUEUED 1=RUNNING 2=SUCCEEDED 3=FAILED',
  input_ref_json LONGTEXT NULL COMMENT '输入引用 JSON',
  output_ref_json LONGTEXT NULL COMMENT '输出引用 JSON',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_eval_run_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测/异步审核流水线运行';

-- ---------------------------------------------------------------------------
-- 安全：用户账号与租户成员
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_user_account (
  id BIGINT NOT NULL COMMENT '自然人账号主键（雪花 Long，由应用 ASSIGN_ID 写入，非库自增）',
  login_name VARCHAR(128) NOT NULL COMMENT '登录名全局唯一，与昵称展示语义分离',
  display_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '昵称/展示名；可与登录名不同；空串时前端可回退登录名',
  password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 等密码哈希',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'UserAccountStatus：0=DISABLED 1=ACTIVE',
  jwt_seq BIGINT NOT NULL DEFAULT 0 COMMENT 'JWT 代际；递增使此前签发的 access token 失效（踢下线/封禁）',
  last_login_at DATETIME(3) NULL COMMENT '最近一次成功登录时间 UTC',
  last_login_ip VARCHAR(64) NULL COMMENT '最近一次成功登录 IP',
  last_login_region VARCHAR(128) NULL COMMENT '登录地区粗粒度（如 CF-IPCountry 国家码）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sec_user_login_name (login_name),
  KEY idx_sec_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自然人登录账号';

CREATE TABLE IF NOT EXISTS sys_tenant_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT 'sys_tenant.id',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  role_code VARCHAR(32) NOT NULL COMMENT 'TenantMemberRole 枚举名：FOUNDER/OWNER/ADMIN/MEMBER',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'UserAccountStatus：0=DISABLED 1=ACTIVE（成员在该租户内是否有效）',
  created_at DATETIME(3) NOT NULL COMMENT '加入时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_member_tenant_user (tenant_id, user_id),
  KEY idx_tenant_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-租户成员及角色';

-- ---------------------------------------------------------------------------
-- 管理端菜单授权（menu_code 与 AdminMenuCode 枚举名一致）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lnk_tenant_admin_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT 'sys_tenant.id',
  menu_code VARCHAR(64) NOT NULL COMMENT 'AdminMenuCode 枚举名，如 USERS、GATEWAY_API',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lnk_tenant_admin_menu (tenant_id, menu_code),
  KEY idx_lnk_tenant_admin_menu_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户级开放的后台菜单码（空表时后端按全量兼容）';

CREATE TABLE IF NOT EXISTS lnk_tenant_user_admin_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT 'sys_tenant.id',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  menu_code VARCHAR(64) NOT NULL COMMENT 'AdminMenuCode；须为租户已开放菜单的子集',
  created_at DATETIME(3) NOT NULL COMMENT '写入时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code),
  KEY idx_lnk_tenant_user_admin_menu_lookup (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员个人后台菜单（与租户菜单求交）';

-- ---------------------------------------------------------------------------
-- 管理端菜单项展示元数据
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_admin_menu_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  menu_code VARCHAR(64) NOT NULL COMMENT '与 AdminMenuCode 枚举名一致',
  title_zh VARCHAR(128) NOT NULL COMMENT '侧栏/配置界面中文名称',
  route_path VARCHAR(256) NULL COMMENT '管理端前端路由 path 提示，如 /users',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState：0=OFF 不展示 1=ON 展示',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_admin_menu_item_code (menu_code),
  KEY idx_sys_admin_menu_item_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端菜单项元数据（重命名、排序等）';

-- ---------------------------------------------------------------------------
-- 网关：按路径 HTTP 限流（gw_* 域前缀）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_api_rate_limit_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NULL COMMENT 'NULL=全局规则；非空=仅匹配该租户上下文',
  path_pattern VARCHAR(512) NOT NULL COMMENT 'Ant 风格路径，如 /api/v1/admin/users/**',
  http_method VARCHAR(16) NOT NULL DEFAULT '*' COMMENT '* 或 ANY=任意方法；否则 GET/POST 等大写方法',
  requests_per_minute INT NOT NULL DEFAULT 120 COMMENT '单窗口（每分钟）内允许的最大请求数',
  enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'ToggleState：0=OFF 1=ON；清单导入默认 0 避免误伤',
  remark VARCHAR(512) NULL COMMENT '人类可读说明',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_gw_rl_tenant (tenant_id),
  KEY idx_gw_rl_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP 接口限流规则';

-- ---------------------------------------------------------------------------
-- 网关：可管理接口目录（供限流规则快捷选择路径/方法；与 gw_api_rate_limit_rule 无物理外键）
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 网关：浏览器 CORS 允许来源（与 WebMvc/Security 动态装配；禁止在代码中写死白名单）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gw_cors_allowed_origin (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  origin VARCHAR(191) NOT NULL COMMENT '完整 Origin（scheme+host+port）；最长 191 以兼容 InnoDB utf8mb4 UNIQUE 索引 767 字节上限',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState：0=OFF 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  remark VARCHAR(512) NULL COMMENT '说明',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gw_cors_origin (origin),
  KEY idx_gw_cors_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CORS 浏览器访问来源白名单';

-- ---------------------------------------------------------------------------
-- 租户可配置 LLM（OpenAI 兼容）；api_key_cipher 为 AES-GCM 密文
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_model (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  alias VARCHAR(64) NOT NULL COMMENT '租户内模型别名（对话选择器展示）',
  display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
  openai_base_url VARCHAR(512) NOT NULL COMMENT 'OpenAI 兼容 Chat Completions Base URL',
  openai_model_id VARCHAR(128) NOT NULL COMMENT '厂商模型 ID',
  fallback_model_alias VARCHAR(64) NULL COMMENT '主备：失败时可切换的租户内模型 alias（LANGUAGE 且启用）',
  model_kind VARCHAR(32) NOT NULL DEFAULT 'LANGUAGE' COMMENT 'LlmModelKind：LANGUAGE/SPEECH/VISION/VECTOR/SMART_ROUTING/WEB_SEARCH',
  integration_backend VARCHAR(48) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT '按 model_kind：VECTOR=LlmVectorBackend 嵌入路径；WEB_SEARCH=LlmWebSearchProvider；其他默认 OPENAI_COMPATIBLE',
  local_deploy TINYINT NOT NULL DEFAULT 0 COMMENT '是否本地部署：1=向量嵌入经 Feign 调 RAG 网关（路径变量为 sys_tenant.code）；0=直连 openai_base_url',
  api_key_cipher MEDIUMTEXT NULL COMMENT 'API Key AES-GCM 密文 Base64；禁止明文',
  allow_anonymous TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许访客调用：0 否 1 是',
  max_attachments INT NOT NULL DEFAULT 10 COMMENT '单轮会话最大附件数',
  supports_thinking TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持思考链：0 否 1 是',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'LlmModelStatus 等：0 停用 1 启用（以 Java 枚举为准）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '列表排序',
  token_quota_total BIGINT NULL COMMENT '共用 token 上限，NULL 不限制',
  tokens_used BIGINT NOT NULL DEFAULT 0 COMMENT '已消耗 token（流式 usage 累加）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_llm_model_tenant_alias (tenant_id, alias),
  KEY idx_llm_model_tenant_status (tenant_id, status),
  KEY idx_llm_model_tenant_kind (tenant_id, model_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户可配置大模型端点';

-- ---------------------------------------------------------------------------
-- 会话附件（抽取文本注入上下文）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_attachment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  conversation_id BIGINT NOT NULL COMMENT 'chat_conversation.id',
  file_name VARCHAR(512) NOT NULL COMMENT '原始文件名',
  mime_type VARCHAR(128) NOT NULL COMMENT 'MIME 类型',
  char_length INT NOT NULL DEFAULT 0 COMMENT '抽取文本字符数',
  extracted_text MEDIUMTEXT NOT NULL COMMENT '抽取后的纯文本',
  created_at DATETIME(3) NOT NULL COMMENT '上传处理完成时间 UTC',
  PRIMARY KEY (id),
  KEY idx_chat_att_conv (tenant_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话附件解析结果';

-- ---------------------------------------------------------------------------
-- 租户运行时配置（免重启；应用 Redis 缓存）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ten_runtime_setting (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT 'sys_tenant.id',
  setting_key VARCHAR(64) NOT NULL COMMENT 'TenantRuntimeSettingKey 存储值，如 AUTH_OPEN_REGISTRATION',
  value_text MEDIUMTEXT NOT NULL COMMENT '文本值：布尔、短数字串、JSON 等（联网多轮后缀可较长）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_runtime_setting_tenant_key (tenant_id, setting_key),
  KEY idx_ten_runtime_setting_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户级运行时键值配置';

-- ---------------------------------------------------------------------------
-- 用户画像片段（对话累积；与 common.profile 对齐）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ten_profile_tag (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  tag_code VARCHAR(64) NOT NULL COMMENT 'ProfileTagCode 存储值',
  tag_value VARCHAR(1024) NOT NULL COMMENT '标签值',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_profile_tag (tenant_id, subject_key, tag_code),
  KEY idx_ten_profile_tenant_subject (tenant_id, subject_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户内用户/设备画像标签';

-- ---------------------------------------------------------------------------
-- 设备绑定与分层记忆（方案三：抽象层 + 具体层；注册归并）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ten_user_device_link (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  device_id VARCHAR(64) NOT NULL COMMENT '与 X-Device-Id 对齐的设备码',
  linked_at DATETIME(3) NOT NULL COMMENT '绑定时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_user_device_link (tenant_id, user_id, device_id),
  KEY idx_ten_user_device_device (tenant_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与设备绑定（注册归并审计用）';

CREATE TABLE IF NOT EXISTS ten_user_memory_abstract (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  body_json LONGTEXT NOT NULL COMMENT '抽象层画像 JSON（稳定特质、偏好等；由规则或异步 LLM 合并）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_user_memory_abstract (tenant_id, subject_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户/设备记忆抽象层';

CREATE TABLE IF NOT EXISTS ten_user_memory_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  conversation_id BIGINT NULL COMMENT '来源会话 chat_conversation.id，可空',
  chunk_role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER=用户侧摘录 ASSISTANT=助手侧摘录',
  content_snippet VARCHAR(2000) NOT NULL COMMENT '具体层可检索片段（用户输入摘录等）',
  vector_ref VARCHAR(64) NULL COMMENT '向量索引侧可选标记（如 milvus）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  KEY idx_ten_user_memory_chunk_subj_time (tenant_id, subject_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户/设备记忆具体层片段';

SET FOREIGN_KEY_CHECKS = 1;

-- #############################################################################
-- 以下为幂等种子与清单（可重复执行段使用 INSERT IGNORE）
-- #############################################################################

-- 默认租户
INSERT INTO sys_tenant (id, code, name, status, created_at, updated_at)
VALUES (1, 'default', 'Default', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status), updated_at = UTC_TIMESTAMP(3);

-- 默认租户：开放注册开关（与历史 application 默认一致）
INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
VALUES (1, 'AUTH_OPEN_REGISTRATION', 'true', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 所有已有租户写入默认运行时配置（幂等）
INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'AUTH_OPEN_REGISTRATION', 'true', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'CHAT_PROMPT_LIMITS_JSON', '{}', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'MEMORY_POLICY_JSON', '{}', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'CHAT_INPUT_GUARD_JSON', '{}', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'OUTBOUND_RESILIENCE_JSON', '{}', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

-- 租户级：全量后台菜单码（AdminMenuCode）
INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, m.menu_code, UTC_TIMESTAMP(3)
FROM sys_tenant t
CROSS JOIN (
    SELECT 'DASHBOARD' AS menu_code
    UNION ALL SELECT 'USERS'
    UNION ALL SELECT 'USER_PROFILES'
    UNION ALL SELECT 'TENANTS'
    UNION ALL SELECT 'ACCESS_LOGS'
    UNION ALL SELECT 'AUDIT_EVENTS'
    UNION ALL SELECT 'METERING'
    UNION ALL SELECT 'LLM_MODELS'
    UNION ALL SELECT 'MCP_SERVERS'
    UNION ALL SELECT 'RAG_KBS'
    UNION ALL SELECT 'FILE_OBJECTS'
    UNION ALL SELECT 'NOTIFICATIONS'
    UNION ALL SELECT 'EVAL_RUNS'
    UNION ALL SELECT 'CHAT'
    UNION ALL SELECT 'CHAT_INTENTS'
    UNION ALL SELECT 'SYSTEM_SETTINGS'
    UNION ALL SELECT 'MENU_CATALOG'
    UNION ALL SELECT 'GATEWAY_API'
) AS m;

-- 用户 admin：各租户下个人后台菜单全量（若存在 username=admin）
INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, m.menu_code, UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin'
CROSS JOIN (
    SELECT 'DASHBOARD' AS menu_code
    UNION ALL SELECT 'USERS'
    UNION ALL SELECT 'USER_PROFILES'
    UNION ALL SELECT 'TENANTS'
    UNION ALL SELECT 'ACCESS_LOGS'
    UNION ALL SELECT 'AUDIT_EVENTS'
    UNION ALL SELECT 'METERING'
    UNION ALL SELECT 'LLM_MODELS'
    UNION ALL SELECT 'MCP_SERVERS'
    UNION ALL SELECT 'RAG_KBS'
    UNION ALL SELECT 'FILE_OBJECTS'
    UNION ALL SELECT 'NOTIFICATIONS'
    UNION ALL SELECT 'EVAL_RUNS'
    UNION ALL SELECT 'CHAT'
    UNION ALL SELECT 'CHAT_INTENTS'
    UNION ALL SELECT 'SYSTEM_SETTINGS'
    UNION ALL SELECT 'MENU_CATALOG'
    UNION ALL SELECT 'GATEWAY_API'
) AS m;

-- 管理端菜单项中文名与路由提示
INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('DASHBOARD', '数据概览', '/dashboard', 5, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('USERS', '用户管理', '/users', 10, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('USER_PROFILES', '用户画像', '/users/profiles', 12, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('TENANTS', '租户管理', '/tenant/tenants', 20, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('MENU_CATALOG', '菜单管理', '/system/menu-items', 25, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('GATEWAY_API', '接口与限流', '/gateway/api-rate-limits', 28, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('ACCESS_LOGS', '访问日志', '/gateway/access-logs', 30, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('AUDIT_EVENTS', '审计事件', '/audit/events', 40, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('METERING', '计量', '/billing/metering', 50, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('LLM_MODELS', '模型管理', '/model/llm-models', 60, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('MCP_SERVERS', 'MCP 服务', '/mcp/servers', 70, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('RAG_KBS', 'RAG 知识库', '/rag/knowledge-bases', 80, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('FILE_OBJECTS', '文件对象', NULL, 90, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('NOTIFICATIONS', '通知订阅', NULL, 100, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('EVAL_RUNS', '评测运行', NULL, 110, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('CHAT', '对话日志', '/chat/conversations', 120, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('CHAT_INTENTS', '意图识别', '/chat/intents', 125, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('SYSTEM_SETTINGS', '系统参数', NULL, 130, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- -----------------------------------------------------------------------------
-- 对话意图默认样例（租户 1；默认关闭 enabled=0）
-- -----------------------------------------------------------------------------
SET @def_tid := 1;
INSERT INTO chat_intent_definition (tenant_id, code, display_name, description, handler_kind, enabled, sort_order, extra_config_json, created_at, updated_at)
SELECT @def_tid, 'travel_reimbursement', '出差报销', '分阶段出差办理；扩展字段 extra_config_json 可配置 Coze 等（见 PROJECT.md）。', 0, 0, 100, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'travel_reimbursement');

SET @travel_iid := (SELECT id FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'travel_reimbursement' LIMIT 1);

INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '出差报销', 0, 1, 10, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '差旅报销', 0, 1, 11, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '报销差旅费', 0, 1, 12, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '继续', 1, 1, 20, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '下一步', 1, 1, 21, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '安排行程', 1, 1, 22, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;

-- -----------------------------------------------------------------------------
-- CORS 默认来源（幂等；本地开发与文档约定端口）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO gw_cors_allowed_origin (origin, enabled, sort_order, remark, created_at, updated_at) VALUES
('http://localhost:5173', 1, 10, '用户端 dev', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://localhost:5174', 1, 11, '管理端 dev（可选端口）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://localhost:5176', 1, 12, '管理端 dev（默认 vite）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://127.0.0.1:5173', 1, 20, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://127.0.0.1:5174', 1, 21, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://127.0.0.1:5176', 1, 22, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- -----------------------------------------------------------------------------
-- 接口限流清单（默认 enabled=0；仅当表为空时批量插入，避免重复执行整文件产生重复行）
-- -----------------------------------------------------------------------------
INSERT INTO gw_api_rate_limit_rule (tenant_id, path_pattern, http_method, requests_per_minute, enabled, remark, created_at, updated_at)
SELECT v.tenant_id, v.path_pattern, v.http_method, v.requests_per_minute, v.enabled, v.remark, v.created_at, v.updated_at
FROM (
    SELECT NULL AS tenant_id, '/open/v1/auth/login' AS path_pattern, 'POST' AS http_method, 60 AS requests_per_minute, 0 AS enabled, '登录' AS remark, UTC_TIMESTAMP(3) AS created_at, UTC_TIMESTAMP(3) AS updated_at
    UNION ALL SELECT NULL, '/open/v1/auth/register', 'POST', 30, 0, '自助注册', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/system/me', 'GET', 120, 0, '开放 me', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/chat/models', 'GET', 120, 0, '对话模型列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/chat/conversations', 'GET', 120, 0, '开放会话列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/chat/conversations', 'POST', 60, 0, '创建会话', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/chat/conversations/*/messages', 'GET', 120, 0, '开放消息列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/chat/conversations/*/messages', 'POST', 30, 0, '流式发消息 SSE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/open/v1/chat/conversations/*/attachments', 'POST', 20, 0, '会话附件上传', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/me', 'GET', 120, 0, '管理端 me', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users', 'GET', 120, 0, '用户列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users', 'POST', 30, 0, '创建用户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*', 'GET', 120, 0, '用户详情', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*', 'PUT', 60, 0, '更新用户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*', 'DELETE', 20, 0, '删除/禁用用户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*/admin-menus', 'GET', 120, 0, '用户个人菜单', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*/admin-menus', 'PUT', 30, 0, '替换用户个人菜单', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*/tenant-role', 'PUT', 30, 0, '调整租户角色', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*/kick-session', 'POST', 30, 0, '踢下线', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/users/*/ban', 'POST', 10, 0, '封禁用户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenants', 'GET', 60, 0, '租户列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenants', 'POST', 20, 0, '创建租户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenants/*', 'PUT', 30, 0, '更新租户', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenants/*/admin-menus', 'GET', 60, 0, '租户后台菜单', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenants/*/admin-menus', 'PUT', 30, 0, '替换租户后台菜单', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/access-logs', 'GET', 120, 0, '访问日志', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/audit-events', 'GET', 120, 0, '审计事件', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/metering-events', 'GET', 120, 0, '计量事件', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/file-objects', 'GET', 120, 0, '文件对象', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/notification-subscriptions', 'GET', 120, 0, '通知订阅', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/eval-runs', 'GET', 120, 0, '评测运行', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/llm-models/meta', 'GET', 60, 0, 'LLM 模型管理 UI 元数据', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/llm-models', 'GET', 120, 0, 'LLM 模型列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/llm-models', 'POST', 20, 0, '创建 LLM 模型', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/llm-models/*', 'PUT', 30, 0, '更新 LLM 模型', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/llm-models/*', 'DELETE', 20, 0, '删除 LLM 模型', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/mcp-servers', 'GET', 120, 0, 'MCP 服务列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/mcp-servers', 'POST', 20, 0, '注册 MCP', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/mcp-servers/*', 'PUT', 30, 0, '更新 MCP', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/mcp-servers/*', 'DELETE', 20, 0, '删除 MCP', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/rag-kbs/**', 'GET', 120, 0, 'RAG 知识库（读，路径含租户编码）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/rag-kbs/**', 'POST', 60, 0, 'RAG 知识库（写）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/rag-kbs/**', 'PUT', 30, 0, 'RAG 知识库 PUT', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/rag-kbs/**', 'PATCH', 120, 0, 'RAG 知识库 PATCH', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/rag-kbs/**', 'DELETE', 60, 0, 'RAG 知识库 DELETE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/conversations', 'GET', 120, 0, '管理端会话列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/conversations/*/messages', 'GET', 120, 0, '管理端消息', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/sensitive-terms/platform', 'GET', 120, 0, '敏感词平台池分页', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/sensitive-terms/tenant', 'GET', 120, 0, '敏感词租户池分页', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/sensitive-terms', 'POST', 30, 0, '新增敏感词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/sensitive-terms/import', 'POST', 10, 0, '批量导入敏感词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/sensitive-terms/*', 'DELETE', 30, 0, '删除敏感词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents', 'GET', 120, 0, '意图列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents', 'POST', 30, 0, '新增意图', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents/*', 'PUT', 30, 0, '更新意图', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents/*', 'DELETE', 20, 0, '删除意图', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents/*/keywords', 'GET', 120, 0, '意图关键词列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents/*/keywords', 'POST', 30, 0, '新增意图关键词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents/*/keywords/*', 'PUT', 30, 0, '更新意图关键词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/chat/intents/*/keywords/*', 'DELETE', 20, 0, '删除意图关键词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenant-runtime-settings', 'GET', 120, 0, '租户运行时配置列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/tenant-runtime-settings', 'PUT', 30, 0, '更新租户运行时配置', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/menu-items', 'GET', 120, 0, '菜单项目录', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/menu-items', 'POST', 20, 0, '新增菜单项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/menu-items/*', 'PUT', 30, 0, '更新菜单项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/menu-items/*', 'DELETE', 20, 0, '删除菜单项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-rate-limits', 'GET', 120, 0, '限流规则分页', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-rate-limits', 'POST', 30, 0, '新建限流规则', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-rate-limits/*', 'PUT', 30, 0, '更新限流规则', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-rate-limits/*', 'DELETE', 20, 0, '删除限流规则', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-api-endpoints', 'GET', 120, 0, '接口目录分页/快捷', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-api-endpoints/picker', 'GET', 120, 0, '接口目录 picker', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-api-endpoints', 'POST', 30, 0, '新建接口目录项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-api-endpoints/*', 'PUT', 30, 0, '更新接口目录项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/gateway-api-endpoints/*', 'DELETE', 20, 0, '删除接口目录项', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/cors-allowed-origins', 'GET', 120, 0, 'CORS 来源列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/cors-allowed-origins', 'POST', 30, 0, '新增 CORS 来源', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/cors-allowed-origins/*', 'PUT', 30, 0, '更新 CORS 来源', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/admin/cors-allowed-origins/*', 'DELETE', 20, 0, '删除 CORS 来源', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/rag/kbs', 'GET', 120, 0, 'RAG 知识库（租户 API）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/rag/kbs', 'POST', 30, 0, '创建知识库（租户 API）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/rag/kbs/*/index-jobs', 'POST', 20, 0, '索引任务（租户 API）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/mcp/tools/**', 'POST', 60, 0, 'MCP 工具调用', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/notifications/webhooks', 'POST', 60, 0, 'Webhook 订阅', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/api/v1/file/presign-upload', 'POST', 60, 0, '预签名上传', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
    UNION ALL SELECT NULL, '/internal/model/**', 'POST', 120, 0, '内部模型补全', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
) AS v
WHERE NOT EXISTS (SELECT 1 FROM gw_api_rate_limit_rule LIMIT 1);

-- #############################################################################
-- 附录：可选一次性运维（按需手工执行，勿纳入自动化重复跑）
-- #############################################################################
-- 将已有用户 admin 在租户 1 下的成员角色提升为创始人（执行前确认 tenant_id / user_id）
-- UPDATE sys_tenant_member m
-- JOIN sec_user_account u ON u.id = m.user_id AND u.login_name = 'admin'
-- SET m.role_code = 'FOUNDER'
-- WHERE m.tenant_id = 1 AND m.role_code IN ('OWNER', 'ADMIN', 'MEMBER');
--
-- 历史库若仍存在表名 sys_llm_model，可一次性重命名（无该表则跳过）：
-- RENAME TABLE sys_llm_model TO llm_model;
