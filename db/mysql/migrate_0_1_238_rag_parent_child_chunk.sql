-- 子母分片：子块向量检索，母块仅作上下文；parent_chunk_id 指向母块主键。
ALTER TABLE rag_chunk
  ADD COLUMN parent_chunk_id BIGINT NULL COMMENT '母分片 rag_chunk.id；NULL=顶层（母块或扁平分片）' AFTER retrieval_enabled,
  ADD KEY idx_rag_chunk_parent (tenant_id, parent_chunk_id);
