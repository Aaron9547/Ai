-- 0.1.236 续跑（与主脚本后半段等价，可重复执行）
-- 适用：主脚本在 ADD 列之后中断（如 1364 / 1060），或仅需补 DROP + INSERT + 网关
-- 更推荐：直接重跑已幂等的 migrate_0_1_236_scheduled_generic_rag_web_crawl_site.sql

SET NAMES utf8mb4;

SET @has_schedule_preset = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ten_scheduled_task' AND column_name = 'schedule_preset'
);

SET @drop_legacy_cols_sql = IF(@has_schedule_preset > 0,
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
PREPARE drop_legacy_cols_stmt FROM @drop_legacy_cols_sql;
EXECUTE drop_legacy_cols_stmt;
DEALLOCATE PREPARE drop_legacy_cols_stmt;

INSERT INTO ten_scheduled_task (tenant_id, task_type, name, enabled, executor_code, cron_expression, last_run_at, created_at, updated_at)
SELECT DISTINCT s.tenant_id, 'RAG_WEB_CRAWL_DISPATCH', '知识库网页爬取调度', 1, 'RAG_WEB_CRAWL_DISPATCH', '0 0 3 * * *', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM rag_web_crawl_site s
WHERE NOT EXISTS (
  SELECT 1 FROM ten_scheduled_task t
  WHERE t.tenant_id = s.tenant_id AND t.executor_code = 'RAG_WEB_CRAWL_DISPATCH'
);

UPDATE ten_scheduled_task SET task_type = executor_code WHERE executor_code IS NOT NULL;

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/rag-kbs/*/web-crawl/sites', 'GET', '知识库爬站配置列表', NULL, 1, 4255, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites', 'POST', '新建知识库爬站配置', NULL, 1, 4256, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites/*', 'PUT', '更新知识库爬站配置', NULL, 1, 4257, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites/*', 'DELETE', '删除知识库爬站配置', NULL, 1, 4258, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/sites/*/run', 'POST', '立即执行知识库爬站', NULL, 1, 4259, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-kbs/*/web-crawl/site-meta', 'GET', '爬站配置枚举', NULL, 1, 4260, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
