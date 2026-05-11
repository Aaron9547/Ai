-- 0.1.174：向量嵌入策略枚举补充 VOLCENGINE_ARK_MULTIMODAL（仅列注释与协作说明；Java/MyBatis 已支持新码值）。
ALTER TABLE llm_model
  MODIFY COLUMN vector_backend VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE'
  COMMENT 'LlmVectorBackend：仅 VECTOR；OPENAI_COMPATIBLE=服务根补 /v1/embeddings；VOLCENGINE_ARK=兼容根仅补 /embeddings；VOLCENGINE_ARK_MULTIMODAL=方舟多模态 /embeddings/multimodal';
