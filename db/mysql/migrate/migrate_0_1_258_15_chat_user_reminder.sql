-- 0.1.258：对话用户提醒 + 内置 MCP parse_reminder + CHAT_USER_REMINDER 定时/邮件场景
-- 已建库须手工执行本脚本后再重启应用。

CREATE TABLE IF NOT EXISTS chat_user_reminder (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  registration_id BIGINT NOT NULL COMMENT 'ten_scheduled_task.id',
  conversation_id BIGINT NULL COMMENT '创建时会话',
  intent_definition_id BIGINT NULL COMMENT 'chat_intent_definition.id',
  title VARCHAR(128) NOT NULL COMMENT '展示标题',
  action_text VARCHAR(512) NOT NULL COMMENT '提醒正文',
  schedule_type TINYINT NOT NULL COMMENT 'ReminderScheduleType',
  cron_expression VARCHAR(128) NOT NULL COMMENT 'Spring 6 段 cron',
  ends_at DATETIME(3) NOT NULL COMMENT '截止 UTC',
  status TINYINT NOT NULL COMMENT 'ChatUserReminderStatus',
  last_sent_at DATETIME(3) NULL COMMENT '上次发送 UTC',
  send_count INT NOT NULL DEFAULT 0 COMMENT '发送次数',
  mcp_request_json MEDIUMTEXT NULL COMMENT 'MCP 请求 JSON',
  mcp_response_json MEDIUMTEXT NULL COMMENT 'MCP 响应 JSON',
  created_at DATETIME(3) NOT NULL COMMENT '创建 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cur_registration (registration_id),
  KEY idx_cur_tenant_user_status (tenant_id, user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话用户全局提醒';

-- 意图种子（租户 1，默认关闭）
SET @def_tid := 1;
INSERT INTO chat_intent_definition (tenant_id, code, display_name, description, handler_kind, enabled, sort_order, extra_config_json, created_at, updated_at)
SELECT @def_tid, 'one_sentence_reminder', '一句话办事', '用户说「提醒我…」后解析时间并发送定时邮件；话术解析使用平台内置服务。', 1, 0, 110,
  '{"handlerParams":{"parseToolKind":"RULE","defaultMaxDays":90,"maxActiveReminders":20}}',
  UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'one_sentence_reminder');

SET @rem_iid := (SELECT id FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'one_sentence_reminder' LIMIT 1);

INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @rem_iid, '提醒我', 0, 1, 10, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @rem_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @rem_iid, '定时提醒', 0, 1, 11, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @rem_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @rem_iid, '取消提醒', 2, 1, 20, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @rem_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @rem_iid, '关闭提醒', 2, 1, 21, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @rem_iid IS NOT NULL;

-- 邮件模板占位（租户 1；须已有 status=1 的 msg_channel；占位符为 {title} 单花括号，与 MessageTemplateSupport 一致）
INSERT INTO msg_template (tenant_id, scene_code, locale, channel_id, subject_template, body_template, status, created_at, updated_at)
SELECT @def_tid, 'CHAT_USER_REMINDER', 'zh-CN', c.id,
  '【提醒】{title}',
  '您好，\n\n这是您设置的提醒：{actionText}\n\n（周期：{scheduleType}）\n',
  1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM msg_channel c
WHERE c.tenant_id = @def_tid AND c.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM msg_template t
    WHERE t.tenant_id = @def_tid AND t.scene_code = 'CHAT_USER_REMINDER' AND t.locale = 'zh-CN')
ORDER BY CASE c.channel_code WHEN 'register_email' THEN 0 WHEN 'knowledge_planet_email' THEN 1 ELSE 2 END
LIMIT 1;
