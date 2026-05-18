-- 0.1.230：对话分享快照（短链只读页）；已建库增量执行；新库以 schema_v1.sql 为准可跳过
CREATE TABLE IF NOT EXISTS chat_conversation_share (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  conversation_id BIGINT NOT NULL COMMENT '来源会话 chat_conversation.id',
  share_code VARCHAR(24) NOT NULL COMMENT '短链码，全局唯一',
  title VARCHAR(256) NULL COMMENT '分享标题（会话名或节选说明）',
  snapshot_json MEDIUMTEXT NOT NULL COMMENT 'JSON：title + messages[]',
  expires_at DATETIME(3) NULL COMMENT '过期时间 UTC；为空则不过期',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_share_code (share_code),
  KEY idx_chat_share_tenant_conv (tenant_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话分享快照（公开短链）';
