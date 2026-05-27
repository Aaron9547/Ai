-- 0.1.241-SNAPSHOT：每日热点改由 ten_scheduled_task 调度（CHAT_STARTER_DAILY_HOT）
-- 已建库须手工执行；幂等。

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
  'CHAT_STARTER_DAILY_HOT',
  '每日推荐热点',
  CASE
    WHEN LOWER(TRIM(COALESCE(en.value_text, 'true'))) = 'false' THEN 0
    ELSE 1
  END,
  'CHAT_STARTER_DAILY_HOT',
  COALESCE(NULLIF(TRIM(cr.value_text), ''), '0 0 6 * * *'),
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM sys_tenant t
LEFT JOIN ten_runtime_setting en
  ON en.tenant_id = t.id AND en.setting_key = 'CHAT_STARTER_DAILY_HOT_ENABLED'
LEFT JOIN ten_runtime_setting cr
  ON cr.tenant_id = t.id AND cr.setting_key = 'CHAT_STARTER_DAILY_HOT_CRON'
WHERE NOT EXISTS (
  SELECT 1
  FROM ten_scheduled_task st
  WHERE st.tenant_id = t.id AND st.executor_code = 'CHAT_STARTER_DAILY_HOT'
);
