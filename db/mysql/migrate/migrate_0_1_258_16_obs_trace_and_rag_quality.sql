-- MCP 跟踪链 / RAG 命中链路 / RAG 质量评测（已建库须手工执行）

CREATE TABLE IF NOT EXISTS obs_mcp_trace_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  trace_id VARCHAR(64) NOT NULL COMMENT '单次 MCP 调用 traceId',
  orchestration_trace_id VARCHAR(64) NULL COMMENT '编排层 traceId',
  http_trace_id VARCHAR(64) NULL COMMENT 'HTTP/MDC traceId',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  user_id BIGINT NULL COMMENT '用户 ID',
  conversation_id BIGINT NULL COMMENT '会话 ID',
  conversation_public_id VARCHAR(64) NULL COMMENT '会话 public_id',
  source_scene VARCHAR(32) NOT NULL COMMENT 'McpTraceSourceScene',
  llm_round INT NULL COMMENT 'MCP 多轮序号',
  qualified_tool_name VARCHAR(256) NOT NULL COMMENT '限定工具名',
  server_id BIGINT NULL COMMENT 'MCP 服务 ID',
  success TINYINT(1) NOT NULL COMMENT '是否成功',
  error_code VARCHAR(128) NULL COMMENT '错误码',
  latency_ms BIGINT NOT NULL COMMENT '耗时毫秒',
  arguments_json TEXT NULL COMMENT '参数摘要',
  result_json TEXT NULL COMMENT '结果摘要',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_obs_mcp_trace_id (trace_id),
  KEY idx_obs_mcp_tenant_time (tenant_id, created_at),
  KEY idx_obs_mcp_conv_time (conversation_id, created_at),
  KEY idx_obs_mcp_orch (orchestration_trace_id),
  KEY idx_obs_mcp_http (http_trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 工具调用跟踪事件';

CREATE TABLE IF NOT EXISTS obs_rag_hit_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  hit_trace_id VARCHAR(64) NOT NULL COMMENT '同批检索 traceId',
  http_trace_id VARCHAR(64) NULL COMMENT 'HTTP traceId',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  user_id BIGINT NULL COMMENT '用户 ID',
  conversation_id BIGINT NULL COMMENT '会话 ID',
  user_message_id BIGINT NULL COMMENT '用户消息 ID',
  assistant_message_id BIGINT NULL COMMENT '助手消息 ID',
  kb_id BIGINT NOT NULL COMMENT '知识库 ID',
  document_id BIGINT NOT NULL COMMENT '文档 ID',
  chunk_id BIGINT NOT NULL COMMENT '分片 ID',
  chunk_seq INT NULL COMMENT '分片序号',
  retrieval_mode VARCHAR(32) NULL COMMENT 'RagRetrievalMode',
  hit_source VARCHAR(16) NULL COMMENT 'RagRetrievalHitSource',
  vector_similarity DECIMAL(8,6) NULL COMMENT '向量相似度',
  keyword_score DECIMAL(8,6) NULL COMMENT 'ES BM25',
  query_text VARCHAR(1024) NULL COMMENT '检索 query',
  rank_in_batch INT NOT NULL COMMENT '同批排名',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  KEY idx_obs_rag_tenant_time (tenant_id, created_at),
  KEY idx_obs_rag_conv_time (conversation_id, created_at),
  KEY idx_obs_rag_chunk_time (chunk_id, created_at),
  KEY idx_obs_rag_kb_time (kb_id, created_at),
  KEY idx_obs_rag_hit_trace (hit_trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 命中链路事件';

CREATE TABLE IF NOT EXISTS rag_quality_assessment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  run_id VARCHAR(64) NOT NULL COMMENT '对外 runId',
  tenant_id BIGINT NOT NULL COMMENT '租户 ID',
  scope VARCHAR(32) NOT NULL COMMENT 'RagQualityAssessmentScope',
  status VARCHAR(16) NOT NULL COMMENT 'EvalRunStatus',
  conversation_id BIGINT NULL COMMENT '会话 ID',
  user_message_id BIGINT NULL COMMENT '用户消息 ID',
  assistant_message_id BIGINT NULL COMMENT '助手消息 ID',
  kb_id BIGINT NULL COMMENT '知识库 ID',
  chunk_id BIGINT NULL COMMENT '分片 ID',
  query_text VARCHAR(1024) NULL COMMENT '检索 query',
  assistant_answer TEXT NULL COMMENT '助手回答快照',
  triggered_by_admin_id BIGINT NULL COMMENT '触发管理员',
  recall_hit_rate DECIMAL(5,4) NULL COMMENT '召回命中率',
  citation_accuracy DECIMAL(5,4) NULL COMMENT '引用准确率',
  faithfulness_score DECIMAL(5,4) NULL COMMENT '答案忠实度',
  result_json LONGTEXT NULL COMMENT '完整报告 JSON',
  error_code VARCHAR(128) NULL COMMENT '错误码',
  error_message VARCHAR(1024) NULL COMMENT '错误信息',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  finished_at DATETIME(3) NULL COMMENT '完成时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_rag_qa_run_id (run_id),
  KEY idx_rag_qa_tenant_time (tenant_id, created_at),
  KEY idx_rag_qa_asst_msg (assistant_message_id),
  KEY idx_rag_qa_chunk_time (chunk_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 质量评测（手动触发）';

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at)
VALUES ('OBSERVABILITY', '链路可观测', '/audit/observability', 45, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'OBSERVABILITY', UTC_TIMESTAMP(3) FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'OBSERVABILITY', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';
