-- 0.1.211：若曾执行过旧版「ADD original_bytes」迁移，执行本脚本删除该列；未添加过该列的库会报错，可忽略。
-- 新环境以 schema_v1.sql 为准，不包含 original_bytes。

ALTER TABLE chat_attachment DROP COLUMN original_bytes;
