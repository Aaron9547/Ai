-- 0.1.236：定时任务通用化（cron + 执行器）；网页爬取站点配置迁至 rag_web_crawl_site

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS rag_web_crawl_site (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  kb_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL COMMENT '站点显示名',
  base_url VARCHAR(2048) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  schedule_preset VARCHAR(64) NOT NULL COMMENT 'ScheduledTaskIntervalPreset，0=仅手动',
  run_at_time TIME NULL COMMENT '计划执行时刻（Asia/Shanghai）',
  last_crawl_at DATETIME(3) NULL,
  first_run_done TINYINT NOT NULL DEFAULT 0,
  category_id BIGINT NULL,
  chunk_strategy SMALLINT NULL,
  sync_mode VARCHAR(64) NULL COMMENT 'RagWebCrawlSyncMode',
  max_depth INT NULL DEFAULT 3,
  filter_crawled TINYINT NULL DEFAULT 1,
  extract_config LONGTEXT NULL COMMENT 'extract config JSON text',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rwcs_tenant_kb (tenant_id, kb_id, enabled),
  KEY idx_rwcs_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库站点定时爬取配置（业务表）';

-- 从 ten_scheduled_task 迁出爬站配置（保留 id，供 rag_web_crawl_url_item.schedule_id）
SET @has_crawl_tasks = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ten_scheduled_task' AND column_name = 'kb_id'
);
SET @migrate_sites_sql = IF(@has_crawl_tasks > 0,
  'INSERT INTO rag_web_crawl_site (
    id, tenant_id, kb_id, name, base_url, enabled, schedule_preset, run_at_time, last_crawl_at,
    first_run_done, category_id, chunk_strategy, sync_mode, max_depth, filter_crawled, created_at, updated_at
  )
  SELECT
    s.id, s.tenant_id, s.kb_id, s.name, s.base_url, s.enabled,
    COALESCE(NULLIF(TRIM(s.schedule_preset), ''''), ''DAILY''),
    s.run_at_time, s.last_run_at,
    s.first_run_done, s.category_id, s.chunk_strategy, s.sync_mode, s.max_depth, s.filter_crawled, s.created_at, s.updated_at
  FROM ten_scheduled_task s
  WHERE s.task_type = ''RAG_WEB_CRAWL_SITE''
    AND NOT EXISTS (SELECT 1 FROM rag_web_crawl_site r WHERE r.id = s.id)',
  'SELECT 1'
);
PREPARE migrate_sites_stmt FROM @migrate_sites_sql;
EXECUTE migrate_sites_stmt;
DEALLOCATE PREPARE migrate_sites_stmt;

SET @delete_crawl_tasks_sql = IF(@has_crawl_tasks > 0,
  'DELETE FROM ten_scheduled_task WHERE task_type = ''RAG_WEB_CRAWL_SITE''',
  'SELECT 1'
);
PREPARE delete_crawl_tasks_stmt FROM @delete_crawl_tasks_sql;
EXECUTE delete_crawl_tasks_stmt;
DEALLOCATE PREPARE delete_crawl_tasks_stmt;

-- 通用调度字段（可重复执行：列已存在则跳过）
SET @has_executor_code = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ten_scheduled_task' AND column_name = 'executor_code'
);
SET @add_dispatch_cols_sql = IF(@has_executor_code = 0,
  'ALTER TABLE ten_scheduled_task
    ADD COLUMN executor_code VARCHAR(64) NULL COMMENT ''TenantScheduledExecutorCode'' AFTER name,
    ADD COLUMN cron_expression VARCHAR(128) NULL COMMENT ''Spring 6 段 cron'' AFTER executor_code,
    ADD COLUMN next_exec_at DATETIME(3) NULL COMMENT ''下次计划执行'' AFTER last_run_at',
  'SELECT 1'
);
PREPARE add_dispatch_cols_stmt FROM @add_dispatch_cols_sql;
EXECUTE add_dispatch_cols_stmt;
DEALLOCATE PREPARE add_dispatch_cols_stmt;

UPDATE ten_scheduled_task
SET executor_code = COALESCE(executor_code, 'RAG_WEB_CRAWL_DISPATCH'),
    cron_expression = COALESCE(cron_expression, '0 0 3 * * *')
WHERE executor_code IS NULL;

-- 先删爬站专用列，再插入调度注册（可重复执行：无旧列则跳过）
SET @has_legacy_site_cols = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ten_scheduled_task' AND column_name = 'schedule_preset'
);
SET @drop_legacy_site_cols_sql = IF(@has_legacy_site_cols > 0,
  'ALTER TABLE ten_scheduled_task
    DROP COLUMN kb_id,
    DROP COLUMN base_url,
    DROP COLUMN category_id,
    DROP COLUMN chunk_strategy,
    DROP COLUMN sync_mode,
    DROP COLUMN max_depth,
    DROP COLUMN filter_crawled,
    DROP COLUMN first_run_done,
    DROP COLUMN schedule_preset,
    DROP COLUMN run_at_time',
  'SELECT 1'
);
PREPARE drop_legacy_site_cols_stmt FROM @drop_legacy_site_cols_sql;
EXECUTE drop_legacy_site_cols_stmt;
DEALLOCATE PREPARE drop_legacy_site_cols_stmt;

-- 每个有爬站配置的租户补一条调度注册（若不存在）
INSERT INTO ten_scheduled_task (tenant_id, task_type, name, enabled, executor_code, cron_expression, last_run_at, created_at, updated_at)
SELECT DISTINCT s.tenant_id, 'RAG_WEB_CRAWL_DISPATCH', '知识库网页爬取调度', 1, 'RAG_WEB_CRAWL_DISPATCH', '0 0 3 * * *', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM rag_web_crawl_site s
WHERE NOT EXISTS (
  SELECT 1 FROM ten_scheduled_task t
  WHERE t.tenant_id = s.tenant_id AND t.executor_code = 'RAG_WEB_CRAWL_DISPATCH'
);

-- task_type 保留作展示；与 executor_code 对齐
UPDATE ten_scheduled_task SET task_type = executor_code WHERE executor_code IS NOT NULL;

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/rag-kbs/*/web-crawl/sites', 'GET', '知识库爬站配置列表', NULL, 1, 4255, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites', 'POST', '新建知识库爬站配置', NULL, 1, 4256, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites/*', 'PUT', '更新知识库爬站配置', NULL, 1, 4257, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites/*', 'DELETE', '删除知识库爬站配置', NULL, 1, 4258, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites/*/run', 'POST', '立即执行知识库爬站', NULL, 1, 4259, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/site-meta', 'GET', '爬站配置枚举', NULL, 1, 4260, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
