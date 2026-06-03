-- 0.1.258 补丁：仅插入 CHAT_USER_REMINDER 邮件模板（修正 msg_channel.status / 占位符 {title}）
-- 适用：已执行过旧版 258_15（表与意图已建好，模板 INSERT 因 c.enabled 失败）时手工执行。

SET @def_tid := 1;

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
