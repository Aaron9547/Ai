-- 0.1.91：llm_model 模型类型（语言/语音/视觉/向量/智能路由）；与火山引擎产品线划分对齐。
-- 已上线库按需执行；空库以 schema_v1.sql 为准可跳过。

ALTER TABLE llm_model
  ADD COLUMN model_kind VARCHAR(32) NOT NULL DEFAULT 'LANGUAGE' COMMENT 'LlmModelKind：LANGUAGE/SPEECH/VISION/VECTOR/SMART_ROUTING' AFTER openai_model_id;

CREATE INDEX idx_llm_model_tenant_kind ON llm_model (tenant_id, model_kind);
