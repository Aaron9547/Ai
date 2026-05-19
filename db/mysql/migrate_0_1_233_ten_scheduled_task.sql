-- 0.1.233：租户隔离通用定时任务（由 rag_web_crawl_schedule 演进；网页爬取为 task_type=RAG_WEB_CRAWL_SITE）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ten_scheduled_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  task_type VARCHAR(64) NOT NULL COMMENT 'TenantScheduledTaskType',
  name VARCHAR(128) NOT NULL COMMENT '任务显示名',
  enabled TINYINT NOT NULL DEFAULT 1,
  schedule_preset VARCHAR(64) NOT NULL COMMENT 'ScheduledTaskIntervalPreset',
  run_at_time TIME NULL COMMENT '计划执行时刻（应用层按 Asia/Shanghai 解释）',
  last_run_at DATETIME(3) NULL,
  first_run_done TINYINT NOT NULL DEFAULT 0,
  kb_id BIGINT NULL COMMENT 'RAG_WEB_CRAWL_SITE：目标知识库',
  base_url VARCHAR(2048) NULL COMMENT 'RAG_WEB_CRAWL_SITE：站点入口',
  category_id BIGINT NULL,
  chunk_strategy SMALLINT NULL,
  sync_mode VARCHAR(64) NULL COMMENT 'RagWebCrawlSyncMode',
  max_depth INT NULL DEFAULT 3,
  filter_crawled TINYINT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tst_tenant_type (tenant_id, task_type, enabled),
  KEY idx_tst_tenant_kb (tenant_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户定时任务（类型枚举扩展）';

-- 仅当旧表存在时迁数据；静态 SQL 在表不存在时解析即失败，故用 PREPARE
SET @has_legacy_schedule = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'rag_web_crawl_schedule'
);
SET @migrate_legacy_sql = IF(@has_legacy_schedule > 0,
  'INSERT INTO ten_scheduled_task (
    id, tenant_id, task_type, name, enabled, schedule_preset, run_at_time, last_run_at,
    first_run_done, kb_id, base_url, category_id, chunk_strategy, sync_mode, max_depth,
    filter_crawled, created_at, updated_at
  )
  SELECT
    s.id, s.tenant_id, ''RAG_WEB_CRAWL_SITE'', s.name, s.enabled, s.schedule_preset, s.run_at_time, s.last_run_at,
    s.first_run_done, s.kb_id, s.base_url, s.category_id, s.chunk_strategy, s.sync_mode, s.max_depth,
    s.filter_crawled, s.created_at, s.updated_at
  FROM rag_web_crawl_schedule s
  WHERE NOT EXISTS (SELECT 1 FROM ten_scheduled_task t WHERE t.id = s.id)',
  'SELECT 1'
);
PREPARE migrate_legacy_stmt FROM @migrate_legacy_sql;
EXECUTE migrate_legacy_stmt;
DEALLOCATE PREPARE migrate_legacy_stmt;

DROP TABLE IF EXISTS rag_web_crawl_schedule;

-- url 追踪表：schedule_id 语义改为 scheduled_task_id（列名保持 schedule_id 以减少破坏性）
-- 若表尚未创建（未跑 231），由 migrate_0_1_231 或本库其它脚本创建；此处仅加注释说明

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/scheduled-tasks/meta', 'GET', '定时任务类型与周期枚举', NULL, 1, 4310, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/scheduled-tasks', 'GET', '租户定时任务列表', NULL, 1, 4320, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/scheduled-tasks', 'POST', '新建租户定时任务', NULL, 1, 4330, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/scheduled-tasks/*', 'PUT', '更新租户定时任务', NULL, 1, 4340, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/scheduled-tasks/*', 'DELETE', '删除租户定时任务', NULL, 1, 4350, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/scheduled-tasks/*/run', 'POST', '立即执行租户定时任务', NULL, 1, 4360, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
