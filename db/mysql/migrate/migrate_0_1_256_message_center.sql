-- 0.1.256-SNAPSHOT：消息发送中心（msg_channel / msg_template / msg_delivery_log）
-- 已建库须手工执行；幂等。
-- 全程不使用 JSON_OBJECT / JSON_EXTRACT / JSON_UNQUOTE（兼容 MySQL 5.6、MariaDB 10.1 等无 JSON 函数环境）。
-- 从 ten_runtime_setting 迁 SMTP 时用 SUBSTRING_INDEX + LIKE 解析 value_text 中的 JSON 字面量。

CREATE TABLE IF NOT EXISTS msg_channel (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  channel_code VARCHAR(64) NOT NULL COMMENT '租户内唯一通道编码',
  channel_type VARCHAR(32) NOT NULL COMMENT 'MessageChannelType',
  name VARCHAR(128) NOT NULL COMMENT '管理端展示名',
  config_json LONGTEXT NOT NULL COMMENT '通道非敏感参数 JSON',
  secret_json LONGTEXT NULL COMMENT '敏感凭证 JSON',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'MessageChannelStatus',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_channel_tenant_code (tenant_id, channel_code),
  KEY idx_msg_channel_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通道配置';

CREATE TABLE IF NOT EXISTS msg_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  scene_code VARCHAR(64) NOT NULL COMMENT 'MessageSceneCode',
  channel_id BIGINT NOT NULL COMMENT 'msg_channel.id',
  subject_template VARCHAR(512) NULL COMMENT '邮件主题模板',
  body_template LONGTEXT NOT NULL COMMENT '正文/短信模板',
  locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'MessageTemplateStatus',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_msg_template_tenant_scene_locale (tenant_id, scene_code, locale),
  KEY idx_msg_template_tenant (tenant_id),
  KEY idx_msg_template_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息业务模板';

CREATE TABLE IF NOT EXISTS msg_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  scene_code VARCHAR(64) NOT NULL COMMENT 'MessageSceneCode',
  channel_id BIGINT NULL COMMENT 'msg_channel.id',
  channel_type VARCHAR(32) NULL COMMENT 'MessageChannelType',
  recipient VARCHAR(256) NOT NULL COMMENT '收件人',
  request_json LONGTEXT NULL COMMENT '请求摘要 JSON',
  provider_msg_id VARCHAR(128) NULL COMMENT '云厂商回执 ID',
  status TINYINT NOT NULL DEFAULT 0 COMMENT 'MessageDeliveryStatus',
  error_code VARCHAR(64) NULL COMMENT '错误码',
  error_message VARCHAR(1024) NULL COMMENT '错误信息',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
  idempotency_key VARCHAR(128) NULL COMMENT '幂等键',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  finished_at DATETIME(3) NULL COMMENT '完成时间 UTC',
  PRIMARY KEY (id),
  KEY idx_msg_delivery_log_tenant_time (tenant_id, created_at),
  KEY idx_msg_delivery_log_scene (tenant_id, scene_code),
  KEY idx_msg_delivery_log_idempotency (tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息发送记录';

-- 从 AUTH_REGISTER_VERIFICATION_JSON 迁移注册邮件通道与模板
INSERT INTO msg_channel (tenant_id, channel_code, channel_type, name, config_json, secret_json, status, created_at, updated_at)
SELECT
  s.tenant_id,
  'register_email',
  'EMAIL_SMTP',
  '注册验证码邮件',
  CONCAT(
    '{',
    '"smtpHost":"', REPLACE(REPLACE(s.smtp_host, '\\', '\\\\'), '"', '\\"'), '"',
    ',"smtpPort":', s.smtp_port,
    ',"username":"', REPLACE(REPLACE(s.smtp_user, '\\', '\\\\'), '"', '\\"'), '"',
    ',"from":"', REPLACE(REPLACE(s.smtp_from, '\\', '\\\\'), '"', '\\"'), '"',
    ',"ssl":', s.smtp_ssl,
    '}'
  ),
  CONCAT('{"password":"', REPLACE(REPLACE(s.smtp_pass, '\\', '\\\\'), '"', '\\"'), '"}'),
  1,
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM (
  SELECT
    rs.tenant_id,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"smtpHost":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"smtpHost":', -1), ',', 1)), '')
    ) AS smtp_host,
    COALESCE(
      CAST(NULLIF(TRIM(BOTH ' }' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"smtpPort":', -1), ',', 1)), '') AS UNSIGNED),
      465
    ) AS smtp_port,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"username":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"username":', -1), ',', 1)), '')
    ) AS smtp_user,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"from":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"from":', -1), ',', 1)), '')
    ) AS smtp_from,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"password":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"password":', -1), ',', 1)), ''),
      ''
    ) AS smtp_pass,
    IF(
      rs.value_text LIKE '%"ssl":true%'
        OR rs.value_text LIKE '%"ssl": true%'
        OR rs.value_text LIKE '%"ssl":1%'
        OR rs.value_text LIKE '%"ssl": 1%',
      'true',
      'false'
    ) AS smtp_ssl
  FROM ten_runtime_setting rs
  WHERE rs.setting_key = 'AUTH_REGISTER_VERIFICATION_JSON'
    AND rs.value_text IS NOT NULL
    AND rs.value_text <> '{}'
    AND (
      rs.value_text LIKE '%"smtpHost":"%'
      OR rs.value_text LIKE '%"smtpHost": "%'
      OR rs.value_text LIKE '%"smtpHost": "%'
    )
) s
WHERE s.smtp_host IS NOT NULL AND s.smtp_host <> ''
  AND NOT EXISTS (
    SELECT 1 FROM msg_channel c
    WHERE c.tenant_id = s.tenant_id AND c.channel_code = 'register_email'
  );

INSERT INTO msg_template (tenant_id, scene_code, channel_id, subject_template, body_template, locale, status, created_at, updated_at)
SELECT
  c.tenant_id,
  'REGISTER_VERIFICATION',
  c.id,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"subjectTemplate":"', -1), '"', 1), ''),
    NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"subjectTemplate":', -1), ',', 1)), ''),
    '【{tenantName}】注册验证码'
  ),
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"bodyTemplate":"', -1), '"', 1), ''),
    NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"bodyTemplate":', -1), ',', 1)), ''),
    '验证码：{code}'
  ),
  'zh-CN',
  1,
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM ten_runtime_setting rs
JOIN msg_channel c ON c.tenant_id = rs.tenant_id AND c.channel_code = 'register_email'
WHERE rs.setting_key = 'AUTH_REGISTER_VERIFICATION_JSON'
  AND NOT EXISTS (
    SELECT 1 FROM msg_template t
    WHERE t.tenant_id = rs.tenant_id AND t.scene_code = 'REGISTER_VERIFICATION' AND t.locale = 'zh-CN'
  );

-- 知识星球周报邮件（独立 SMTP，未复用注册邮箱）
INSERT INTO msg_channel (tenant_id, channel_code, channel_type, name, config_json, secret_json, status, created_at, updated_at)
SELECT
  s.tenant_id,
  'knowledge_planet_email',
  'EMAIL_SMTP',
  '知识星球周报邮件',
  CONCAT(
    '{',
    '"smtpHost":"', REPLACE(REPLACE(s.smtp_host, '\\', '\\\\'), '"', '\\"'), '"',
    ',"smtpPort":', s.smtp_port,
    ',"username":"', REPLACE(REPLACE(s.smtp_user, '\\', '\\\\'), '"', '\\"'), '"',
    ',"from":"', REPLACE(REPLACE(s.smtp_from, '\\', '\\\\'), '"', '\\"'), '"',
    ',"ssl":', s.smtp_ssl,
    '}'
  ),
  CONCAT('{"password":"', REPLACE(REPLACE(s.smtp_pass, '\\', '\\\\'), '"', '\\"'), '"}'),
  s.channel_status,
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM (
  SELECT
    rs.tenant_id,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"smtpHost":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"smtpHost":', -1), ',', 1)), '')
    ) AS smtp_host,
    COALESCE(
      CAST(NULLIF(TRIM(BOTH ' }' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"smtpPort":', -1), ',', 1)), '') AS UNSIGNED),
      465
    ) AS smtp_port,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"username":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"username":', -1), ',', 1)), '')
    ) AS smtp_user,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"from":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"from":', -1), ',', 1)), '')
    ) AS smtp_from,
    COALESCE(
      NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"password":"', -1), '"', 1), ''),
      NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"password":', -1), ',', 1)), ''),
      ''
    ) AS smtp_pass,
    IF(
      rs.value_text LIKE '%"ssl":true%'
        OR rs.value_text LIKE '%"ssl": true%'
        OR rs.value_text LIKE '%"ssl":1%'
        OR rs.value_text LIKE '%"ssl": 1%',
      'true',
      'false'
    ) AS smtp_ssl,
    CASE
      WHEN rs.value_text LIKE '%"enabled":true%'
        OR rs.value_text LIKE '%"enabled": true%'
        OR rs.value_text LIKE '%"enabled":1%'
        OR rs.value_text LIKE '%"enabled": 1%'
      THEN 1
      ELSE 0
    END AS channel_status
  FROM ten_runtime_setting rs
  WHERE rs.setting_key = 'KNOWLEDGE_PLANET_EMAIL_JSON'
    AND rs.value_text IS NOT NULL
    AND rs.value_text <> '{}'
    AND NOT (
      rs.value_text LIKE '%"reuseRegisterSmtp":true%'
      OR rs.value_text LIKE '%"reuseRegisterSmtp": true%'
      OR rs.value_text LIKE '%"reuseRegisterSmtp":1%'
      OR rs.value_text LIKE '%"reuseRegisterSmtp": 1%'
    )
    AND (
      rs.value_text LIKE '%"smtpHost":"%'
      OR rs.value_text LIKE '%"smtpHost": "%'
    )
) s
WHERE s.smtp_host IS NOT NULL AND s.smtp_host <> ''
  AND NOT EXISTS (
    SELECT 1 FROM msg_channel c
    WHERE c.tenant_id = s.tenant_id AND c.channel_code = 'knowledge_planet_email'
  );

-- 复用注册 SMTP 的知识星球：模板指向 register_email 通道
INSERT INTO msg_template (tenant_id, scene_code, channel_id, subject_template, body_template, locale, status, created_at, updated_at)
SELECT
  rs.tenant_id,
  'KNOWLEDGE_PLANET_WEEKLY',
  c.id,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"subjectTemplate":"', -1), '"', 1), ''),
    NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"subjectTemplate":', -1), ',', 1)), ''),
    '【{tenantName}】知识星球周报'
  ),
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"bodyTemplate":"', -1), '"', 1), ''),
    NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"bodyTemplate":', -1), ',', 1)), ''),
    '{summary}'
  ),
  'zh-CN',
  CASE
    WHEN rs.value_text LIKE '%"enabled":true%'
      OR rs.value_text LIKE '%"enabled": true%'
      OR rs.value_text LIKE '%"enabled":1%'
      OR rs.value_text LIKE '%"enabled": 1%'
    THEN 1
    ELSE 0
  END,
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM ten_runtime_setting rs
JOIN msg_channel c ON c.tenant_id = rs.tenant_id AND c.channel_code = 'register_email'
WHERE rs.setting_key = 'KNOWLEDGE_PLANET_EMAIL_JSON'
  AND (
    rs.value_text LIKE '%"reuseRegisterSmtp":true%'
    OR rs.value_text LIKE '%"reuseRegisterSmtp": true%'
    OR rs.value_text LIKE '%"reuseRegisterSmtp":1%'
    OR rs.value_text LIKE '%"reuseRegisterSmtp": 1%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM msg_template t
    WHERE t.tenant_id = rs.tenant_id AND t.scene_code = 'KNOWLEDGE_PLANET_WEEKLY' AND t.locale = 'zh-CN'
  );

-- 独立 SMTP 的知识星球模板
INSERT INTO msg_template (tenant_id, scene_code, channel_id, subject_template, body_template, locale, status, created_at, updated_at)
SELECT
  c.tenant_id,
  'KNOWLEDGE_PLANET_WEEKLY',
  c.id,
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"subjectTemplate":"', -1), '"', 1), ''),
    NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"subjectTemplate":', -1), ',', 1)), ''),
    '【{tenantName}】知识星球周报'
  ),
  COALESCE(
    NULLIF(SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"bodyTemplate":"', -1), '"', 1), ''),
    NULLIF(TRIM(BOTH ' "' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"bodyTemplate":', -1), ',', 1)), ''),
    '{summary}'
  ),
  'zh-CN',
  CASE
    WHEN rs.value_text LIKE '%"enabled":true%'
      OR rs.value_text LIKE '%"enabled": true%'
      OR rs.value_text LIKE '%"enabled":1%'
      OR rs.value_text LIKE '%"enabled": 1%'
    THEN 1
    ELSE 0
  END,
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM ten_runtime_setting rs
JOIN msg_channel c ON c.tenant_id = rs.tenant_id AND c.channel_code = 'knowledge_planet_email'
WHERE rs.setting_key = 'KNOWLEDGE_PLANET_EMAIL_JSON'
  AND NOT (
    rs.value_text LIKE '%"reuseRegisterSmtp":true%'
    OR rs.value_text LIKE '%"reuseRegisterSmtp": true%'
    OR rs.value_text LIKE '%"reuseRegisterSmtp":1%'
    OR rs.value_text LIKE '%"reuseRegisterSmtp": 1%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM msg_template t
    WHERE t.tenant_id = rs.tenant_id AND t.scene_code = 'KNOWLEDGE_PLANET_WEEKLY' AND t.locale = 'zh-CN'
  );

-- 精简 AUTH_REGISTER_VERIFICATION_JSON：移除 email 节点，保留验证码参数（无 JSON 函数）
UPDATE ten_runtime_setting rs
SET rs.value_text = CONCAT(
  '{',
  '"codeLength":',
  COALESCE(
    CAST(NULLIF(TRIM(BOTH ' }' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"codeLength":', -1), ',', 1)), '') AS UNSIGNED),
    6
  ),
  ',"codeTtlSeconds":',
  COALESCE(
    CAST(NULLIF(TRIM(BOTH ' }' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"codeTtlSeconds":', -1), ',', 1)), '') AS UNSIGNED),
    600
  ),
  ',"sendCooldownSeconds":',
  COALESCE(
    CAST(NULLIF(TRIM(BOTH ' }' FROM SUBSTRING_INDEX(SUBSTRING_INDEX(rs.value_text, '"sendCooldownSeconds":', -1), ',', 1)), '') AS UNSIGNED),
    60
  ),
  '}'
),
rs.updated_at = UTC_TIMESTAMP(3)
WHERE rs.setting_key = 'AUTH_REGISTER_VERIFICATION_JSON'
  AND rs.value_text IS NOT NULL
  AND (
    rs.value_text LIKE '%"email"%'
    OR rs.value_text LIKE '%"smtpHost"%'
  );

-- 知识星球邮件开关（由 LIKE 推断 enabled，无法解析时默认 false）
INSERT INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT
  rs.tenant_id,
  'KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED',
  CASE
    WHEN rs.value_text LIKE '%"enabled":true%'
      OR rs.value_text LIKE '%"enabled": true%'
      OR rs.value_text LIKE '%"enabled":1%'
      OR rs.value_text LIKE '%"enabled": 1%'
    THEN 'true'
    ELSE 'false'
  END,
  UTC_TIMESTAMP(3),
  UTC_TIMESTAMP(3)
FROM ten_runtime_setting rs
WHERE rs.setting_key = 'KNOWLEDGE_PLANET_EMAIL_JSON'
  AND NOT EXISTS (
    SELECT 1 FROM ten_runtime_setting x
    WHERE x.tenant_id = rs.tenant_id AND x.setting_key = 'KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED'
  );

DELETE FROM ten_runtime_setting
WHERE setting_key = 'KNOWLEDGE_PLANET_EMAIL_JSON';
