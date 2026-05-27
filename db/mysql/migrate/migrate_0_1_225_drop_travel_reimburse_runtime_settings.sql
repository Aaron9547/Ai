-- 出差报销 Coze 配置已迁至 chat_intent_definition.extra_config_json.handlerParams；清理历史 ten_runtime_setting 行。
DELETE FROM ten_runtime_setting
WHERE setting_key IN (
    'TRAVEL_REIMBURSE_COZE_DOMAIN',
    'TRAVEL_REIMBURSE_DOC_COZE_API_KEY',
    'TRAVEL_REIMBURSE_DOC_WORKFLOW_ID',
    'TRAVEL_REIMBURSE_PLAN_COZE_API_KEY',
    'TRAVEL_REIMBURSE_PLAN_WORKFLOW_ID'
);
