-- 0.1.218：llm_model.vector_backend 重命名为 integration_backend，与 web_search_provider 合并为同一列（按 model_kind 解释）；删除 web_search_provider。
-- 若库中尚无 web_search_provider（未执行 0.1.217），@has_web=0，仅重命名列。

SET @has_web := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'llm_model'
    AND COLUMN_NAME = 'web_search_provider'
);

SET @merge_sql := IF(
  @has_web > 0,
  'UPDATE llm_model SET vector_backend = web_search_provider WHERE model_kind = ''WEB_SEARCH'' AND web_search_provider IS NOT NULL AND CHAR_LENGTH(TRIM(web_search_provider)) > 0',
  'SELECT 1'
);
PREPARE merge_stmt FROM @merge_sql;
EXECUTE merge_stmt;
DEALLOCATE PREPARE merge_stmt;

ALTER TABLE llm_model
  CHANGE COLUMN vector_backend integration_backend VARCHAR(48) NOT NULL DEFAULT 'OPENAI_COMPATIBLE'
  COMMENT '按 model_kind：VECTOR=LlmVectorBackend 嵌入路径；WEB_SEARCH=LlmWebSearchProvider；其他默认 OPENAI_COMPATIBLE';

SET @drop_sql := IF(
  @has_web > 0,
  'ALTER TABLE llm_model DROP COLUMN web_search_provider',
  'SELECT 1'
);
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;
