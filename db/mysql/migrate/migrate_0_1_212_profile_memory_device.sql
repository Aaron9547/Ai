-- 0.1.212：设备与用户绑定、分层记忆（抽象/具体）、注册后访客数据归并可审计。
-- 新环境以 schema_v1.sql 为准已含下列表时可跳过。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ten_user_device_link (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  device_id VARCHAR(64) NOT NULL COMMENT '与 X-Device-Id 对齐的设备码',
  linked_at DATETIME(3) NOT NULL COMMENT '绑定时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_user_device_link (tenant_id, user_id, device_id),
  KEY idx_ten_user_device_device (tenant_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与设备绑定（注册归并审计用）';

CREATE TABLE IF NOT EXISTS ten_user_memory_abstract (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  body_json LONGTEXT NOT NULL COMMENT '抽象层画像 JSON（稳定特质、偏好等；由规则或异步 LLM 合并）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_user_memory_abstract (tenant_id, subject_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户/设备记忆抽象层';

CREATE TABLE IF NOT EXISTS ten_user_memory_chunk (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  conversation_id BIGINT NULL COMMENT '来源会话 chat_conversation.id，可空',
  content_snippet VARCHAR(2000) NOT NULL COMMENT '具体层可检索片段（用户输入摘录等）',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  KEY idx_ten_user_memory_chunk_subj_time (tenant_id, subject_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户/设备记忆具体层片段';
