-- 对话首条 system 预算、记忆策略、输入护栏：租户运行参数（空对象表示代码默认）
-- 单条 INSERT，避免部分 JDBC/「执行脚本」客户端在多语句批次上出现 S1009（No operations allowed after statement closed）。
INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, k.setting_key, k.value_text, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t
CROSS JOIN (
    SELECT 'CHAT_PROMPT_LIMITS_JSON' AS setting_key, '{}' AS value_text
    UNION ALL
    SELECT 'MEMORY_POLICY_JSON', '{}'
    UNION ALL
    SELECT 'CHAT_INPUT_GUARD_JSON', '{}'
) AS k;
