-- 0.1.213：记忆具体层 chunk 角色（用户/助手双线）、可选向量引用；供 Milvus 与异步抽象层刷新。
SET NAMES utf8mb4;

ALTER TABLE ten_user_memory_chunk
  ADD COLUMN chunk_role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT 'USER=用户侧摘录 ASSISTANT=助手侧摘录' AFTER conversation_id,
  ADD COLUMN vector_ref VARCHAR(64) NULL COMMENT '向量索引侧可选标记（如 milvus）' AFTER content_snippet;
