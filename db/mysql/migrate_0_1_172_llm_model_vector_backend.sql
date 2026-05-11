-- 0.1.172：llm_model 向量嵌入路径策略（与 schema_v1.llm_model.vector_backend 一致）。
-- 已含该列的空库/已对齐库请勿执行；仅缺列的存量库执行一次。

ALTER TABLE llm_model
  ADD COLUMN vector_backend VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT 'LlmVectorBackend：仅 VECTOR；OPENAI_COMPATIBLE=服务根补 /v1/embeddings；VOLCENGINE_ARK=兼容根仅补 /embeddings' AFTER model_kind;

-- 存量 VECTOR 在引入本列前均按「仅追加 /embeddings」工作；显式回写以免默认 OPENAI_COMPATIBLE 破坏已配置的兼容根。
UPDATE llm_model SET vector_backend = 'VOLCENGINE_ARK' WHERE model_kind = 'VECTOR';
