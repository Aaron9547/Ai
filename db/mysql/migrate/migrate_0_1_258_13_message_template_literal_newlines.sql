-- 0.1.258-SNAPSHOT：消息模板正文中字面量 \n / \r\n 还原为真实换行（注册/周报等 SMTP 模板）
-- 常见于 migrate_0_1_256 从 JSON 抽取 bodyTemplate；已建库可手工执行；幂等。

UPDATE msg_template
SET
  body_template = REPLACE(REPLACE(REPLACE(body_template, '\\r\\n', CHAR(10)), '\\n', CHAR(10)), '\\r', CHAR(10)),
  subject_template = REPLACE(REPLACE(REPLACE(subject_template, '\\r\\n', CHAR(10)), '\\n', CHAR(10)), '\\r', CHAR(10)),
  updated_at = UTC_TIMESTAMP(3)
WHERE body_template LIKE '%\\n%'
   OR body_template LIKE '%\\r%'
   OR subject_template LIKE '%\\n%'
   OR subject_template LIKE '%\\r%';
