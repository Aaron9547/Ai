-- 0.1.145：知识库「参与对话检索」开关（ToggleState 落库）
-- 已上线库按需执行；空库请直接以 schema_v1.sql 为准。

ALTER TABLE rag_knowledge_base
  ADD COLUMN chat_retrieval_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState：是否在对话编排中纳入本知识库检索（0=OFF 1=ON）' AFTER assigned_llm_model_id;
