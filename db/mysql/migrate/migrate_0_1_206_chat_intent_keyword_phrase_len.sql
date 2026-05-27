-- 0.1.206：收敛 chat_intent_keyword.phrase 长度，避免 utf8mb4 下 UNIQUE(intent_id, phrase) 超过 InnoDB 767 字节上限（错误 1071）。
-- 适用：曾用 VARCHAR(255) 建表或从旧脚本迁出；新环境以 schema_v1.sql / migrate_0_1_205_chat_intent.sql 为准时通常无需执行。
-- 若表中已有短语超过 128 字符，请先手工缩短后再执行。

ALTER TABLE chat_intent_keyword
  MODIFY COLUMN phrase VARCHAR(128) NOT NULL COMMENT '触发短语（≤128 字符，兼容 utf8mb4 767 字节唯一索引）';
