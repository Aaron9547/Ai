-- 0.1.254-SNAPSHOT：个人知识星球（沉淀节点、周度方案、定时任务、Open API）
-- 已建库须手工执行；幂等。

CREATE TABLE IF NOT EXISTS ten_user_knowledge_node (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  conversation_id BIGINT NULL COMMENT '来源会话 chat_conversation.id',
  message_id BIGINT NULL COMMENT '来源助手消息 chat_message.id',
  title VARCHAR(255) NOT NULL COMMENT '知识节点标题',
  summary VARCHAR(2000) NOT NULL COMMENT '节点摘要',
  topic_tags_json VARCHAR(1024) NULL COMMENT '主题标签 JSON 数组',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  KEY idx_ten_user_knowledge_node_subj_time (tenant_id, subject_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人知识星球·对话沉淀节点';

CREATE TABLE IF NOT EXISTS ten_user_weekly_insight (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  week_start DATE NOT NULL COMMENT '自然周周一（Asia/Shanghai）',
  status TINYINT NOT NULL DEFAULT 0 COMMENT 'KnowledgeWeeklyInsightStatus',
  plan_json LONGTEXT NULL COMMENT '周度成长方案 JSON',
  computed_at DATETIME(3) NULL COMMENT '方案计算完成时间 UTC',
  emailed_at DATETIME(3) NULL COMMENT '邮件发送时间 UTC',
  error_message VARCHAR(512) NULL COMMENT '失败或跳过原因',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_user_weekly_insight (tenant_id, user_id, week_start),
  KEY idx_ten_user_weekly_insight_week (tenant_id, week_start, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人知识星球·周度成长方案';

INSERT INTO ten_scheduled_task (
  tenant_id,
  task_type,
  name,
  enabled,
  executor_code,
  cron_expression,
  created_at,
  updated_at
)
SELECT
  t.id,
  'KNOWLEDGE_PLANET_WEEKLY_COMPUTE',
  '知识星球·周一方案计算',
  CASE
    WHEN LOWER(TRIM(COALESCE(en.value_text, 'false'))) = 'true' THEN 1
    ELSE 0
  END,
  'KNOWLEDGE_PLANET_WEEKLY_COMPUTE',
  COALESCE(NULLIF(TRIM(cr.value_text), ''), '0 0 3 * * MON'),
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM sys_tenant t
LEFT JOIN ten_runtime_setting en
  ON en.tenant_id = t.id AND en.setting_key = 'KNOWLEDGE_PLANET_ENABLED'
LEFT JOIN ten_runtime_setting cr
  ON cr.tenant_id = t.id AND cr.setting_key = 'KNOWLEDGE_PLANET_WEEKLY_COMPUTE_CRON'
WHERE NOT EXISTS (
  SELECT 1 FROM ten_scheduled_task st
  WHERE st.tenant_id = t.id AND st.executor_code = 'KNOWLEDGE_PLANET_WEEKLY_COMPUTE'
);

INSERT INTO ten_scheduled_task (
  tenant_id,
  task_type,
  name,
  enabled,
  executor_code,
  cron_expression,
  created_at,
  updated_at
)
SELECT
  t.id,
  'KNOWLEDGE_PLANET_WEEKLY_EMAIL',
  '知识星球·周一邮件推送',
  CASE
    WHEN LOWER(TRIM(COALESCE(en.value_text, 'false'))) = 'true' THEN 1
    ELSE 0
  END,
  'KNOWLEDGE_PLANET_WEEKLY_EMAIL',
  COALESCE(NULLIF(TRIM(em.value_text), ''), '0 0 9 * * MON'),
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM sys_tenant t
LEFT JOIN ten_runtime_setting en
  ON en.tenant_id = t.id AND en.setting_key = 'KNOWLEDGE_PLANET_ENABLED'
LEFT JOIN ten_runtime_setting em
  ON em.tenant_id = t.id AND em.setting_key = 'KNOWLEDGE_PLANET_WEEKLY_EMAIL_CRON'
WHERE NOT EXISTS (
  SELECT 1 FROM ten_scheduled_task st
  WHERE st.tenant_id = t.id AND st.executor_code = 'KNOWLEDGE_PLANET_WEEKLY_EMAIL'
);

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
('/open/v1/chat/knowledge-planet/summary', 'GET', 'C端知识星球摘要', NULL, 1, 2112, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/knowledge-planet/universe', 'GET', 'C端知识星球星系', NULL, 1, 2113, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/knowledge-planet/weekly/latest', 'GET', 'C端知识星球最新周报', NULL, 1, 2114, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
