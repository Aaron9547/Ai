-- 0.1.96：分片检索开关（管理端启用/禁用参与 RAG 召回）
ALTER TABLE rag_chunk
  ADD COLUMN retrieval_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'RagChunkRetrievalEnabled：1=ENABLED 参与检索，0=DISABLED' AFTER embedding_ref;
