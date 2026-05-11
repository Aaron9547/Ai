-- 0.1.90：知识中心工作台（分片策略、模型绑定、文档/分片逻辑删除与元数据）
-- 已上线库按需执行；空库请直接以 schema_v1.sql 为准。

ALTER TABLE rag_knowledge_base
  ADD COLUMN default_chunk_strategy SMALLINT NOT NULL DEFAULT 1 COMMENT 'RagChunkStrategy：0=NONE 1=FIXED_CHAR 2=SEMANTIC 3=SLIDING_WINDOW 99=CUSTOM' AFTER name,
  ADD COLUMN chunk_fixed_chars INT NOT NULL DEFAULT 800 COMMENT '固定字数分片目标长度（字符）' AFTER default_chunk_strategy,
  ADD COLUMN chunk_slide_overlap INT NOT NULL DEFAULT 120 COMMENT '滑动窗口重叠字符数' AFTER chunk_fixed_chars,
  ADD COLUMN assigned_llm_model_id BIGINT NULL COMMENT '绑定的租户可配模型 llm_model.id（对话/检索路由用，可空）' AFTER chunk_slide_overlap;

ALTER TABLE rag_document
  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0=有效 1=逻辑删除' AFTER tenant_id,
  ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'FILE' COMMENT 'RagDocumentSourceType 码' AFTER deleted,
  ADD COLUMN content_length BIGINT NOT NULL DEFAULT 0 COMMENT '正文长度（字符或字节语义与 md_content 一致）' AFTER md_content,
  ADD COLUMN original_filename VARCHAR(512) NULL COMMENT '上传文件名（网页入库可空）' AFTER source_uri,
  ADD COLUMN deleted_at DATETIME(3) NULL COMMENT '逻辑删除时间 UTC' AFTER updated_at;

ALTER TABLE rag_chunk
  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0=有效 1=逻辑删除（随文档删除或单独隐藏）' AFTER tenant_id,
  ADD COLUMN updated_at DATETIME(3) NULL COMMENT '更新时间 UTC' AFTER created_at;

CREATE INDEX idx_rag_doc_tenant_deleted ON rag_document (tenant_id, deleted);
CREATE INDEX idx_rag_chunk_tenant_deleted ON rag_chunk (tenant_id, deleted);
