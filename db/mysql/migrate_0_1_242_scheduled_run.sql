-- 0.1.242-SNAPSHOT：定时任务异步执行 run + 进度（ten_scheduled_run）
-- 已建库须手工执行；幂等。

CREATE TABLE IF NOT EXISTS ten_scheduled_run (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  registration_id BIGINT NOT NULL COMMENT 'ten_scheduled_task.id',
  executor_code VARCHAR(64) NOT NULL COMMENT 'TenantScheduledExecutorCode',
  status TINYINT NOT NULL COMMENT 'ScheduledRunStatus：0=PENDING 1=RUNNING 2=SUCCEEDED 3=FAILED',
  trigger_type VARCHAR(16) NOT NULL COMMENT 'ScheduledRunTrigger：MANUAL/CRON',
  progress_json MEDIUMTEXT NULL COMMENT 'LongRunningTaskProgress JSON',
  child_job_task_ids_json MEDIUMTEXT NULL COMMENT '子 job_task.id JSON 数组',
  error_message VARCHAR(2048) NULL COMMENT '失败摘要',
  started_at DATETIME(3) NULL COMMENT '开始执行 UTC',
  finished_at DATETIME(3) NULL COMMENT '结束 UTC',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  KEY idx_tsr_tenant_reg_status (tenant_id, registration_id, status),
  KEY idx_tsr_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户定时任务单次执行（异步+进度）';

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
('/api/v1/admin/scheduled-tasks/*/run/active', 'GET', '定时任务当前执行进度', NULL, 1, 4361, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/scheduled-tasks/runs/*', 'GET', '定时任务执行 run 详情', NULL, 1, 4362, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/job-tasks/*', 'GET', '异步任务详情（含进度）', NULL, 1, 4380, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
