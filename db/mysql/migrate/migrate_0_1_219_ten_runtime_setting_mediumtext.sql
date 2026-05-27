-- ten_runtime_setting.value_text 扩为 MEDIUMTEXT，以容纳较长 JSON（如联网多轮后缀）。
-- 本文件仅含 DDL，单条语句；与 `migrate_0_1_219_ten_runtime_web_search_grounding.sql` 拆文件，
-- 避免部分 JDBC 客户端在同一「执行脚本」批次内 DDL 接 DML 报 S1009（statement closed）。
ALTER TABLE ten_runtime_setting
  MODIFY COLUMN value_text MEDIUMTEXT NOT NULL COMMENT '文本值：布尔、短数字串、JSON 等';
