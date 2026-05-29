-- 0.1.258：chat_conversation 增加对外 public_id（开放 API 路径不再暴露自增主键）
ALTER TABLE chat_conversation
  ADD COLUMN public_id VARCHAR(24) NULL COMMENT '对外会话标识（URL/API），非自增' AFTER id;

UPDATE chat_conversation c
SET c.public_id = CONCAT(
        SUBSTRING(REPLACE(UUID(), '-', ''), 1, 12),
        LPAD(c.id, 4, '0'))
WHERE c.public_id IS NULL OR c.public_id = '';

ALTER TABLE chat_conversation
  MODIFY COLUMN public_id VARCHAR(24) NOT NULL COMMENT '对外会话标识（URL/API），非自增',
  ADD UNIQUE KEY uk_chat_conv_public_id (public_id);
