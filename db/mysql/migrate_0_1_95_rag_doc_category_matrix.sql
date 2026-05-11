-- 0.1.95：知识库文档分类、展示状态、适用范围、上传人；已上线库按需执行。

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

ALTER TABLE rag_document
  ADD COLUMN category_id BIGINT NULL COMMENT 'rag_kb_document_category.id' AFTER content_length,
  ADD COLUMN display_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'RagDocumentDisplayStatus' AFTER category_id,
  ADD COLUMN applicable_scope VARCHAR(512) NULL COMMENT '适用范围说明' AFTER display_status,
  ADD COLUMN uploaded_by_user_id BIGINT NULL COMMENT '上传人 sec_user_account.id' AFTER applicable_scope;

CREATE INDEX idx_rag_doc_category ON rag_document (tenant_id, category_id);
CREATE INDEX idx_rag_doc_display_status ON rag_document (tenant_id, display_status);
