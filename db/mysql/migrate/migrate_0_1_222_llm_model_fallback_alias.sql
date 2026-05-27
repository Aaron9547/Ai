-- 语言模型主备：主模型失败时可按链尝试备用别名（见 DefaultModelCompletionEngine）
ALTER TABLE llm_model
  ADD COLUMN fallback_model_alias VARCHAR(64) NULL COMMENT '主备：失败时可切换的租户内模型 alias，须为 LANGUAGE 且已启用' AFTER openai_model_id;
