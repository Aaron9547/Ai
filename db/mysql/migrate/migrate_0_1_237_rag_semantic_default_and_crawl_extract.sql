-- 0.1.237：新建知识库默认语义分片；爬站正文抽取配置；分片预览 API

SET NAMES utf8mb4;

-- 仅影响此后 INSERT 的新行；存量知识库不修改
ALTER TABLE rag_knowledge_base
  MODIFY COLUMN default_chunk_strategy SMALLINT NOT NULL DEFAULT 2
    COMMENT 'RagChunkStrategy：0=NONE 1=FIXED_CHAR 2=SEMANTIC 3=SLIDING_WINDOW 99=CUSTOM',
  MODIFY COLUMN chunk_fixed_chars INT NOT NULL DEFAULT 1000 COMMENT '固定/语义分片目标长度（字符）';

SET @has_rwcs_extract = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'rag_web_crawl_site' AND column_name = 'extract_config'
);
SET @add_rwcs_extract_sql = IF(@has_rwcs_extract = 0,
  'ALTER TABLE rag_web_crawl_site ADD COLUMN extract_config LONGTEXT NULL COMMENT ''extract config JSON text'' AFTER filter_crawled',
  'SELECT 1');
PREPARE add_rwcs_extract_stmt FROM @add_rwcs_extract_sql;
EXECUTE add_rwcs_extract_stmt;
DEALLOCATE PREPARE add_rwcs_extract_stmt;

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/rag-kbs/*/*/ingest/preview-chunks', 'POST', '入库前分片预览', NULL, 1, 4261, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
