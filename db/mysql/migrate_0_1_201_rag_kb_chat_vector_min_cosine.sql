-- 0.1.201：知识库级对话向量召回 COSINE 下限（每库独立；多库对话检索各用各库值）
ALTER TABLE rag_knowledge_base
  ADD COLUMN chat_vector_min_cosine_score DECIMAL(5,4) NOT NULL DEFAULT 0.6500
  COMMENT '对话侧 Milvus COSINE 分数下限（按知识库）；0=关闭该库向量阈值过滤'
  AFTER chat_retrieval_enabled;
