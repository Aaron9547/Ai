-- 0.1.80：sec_user_account 登录列重命名 + 主键去自增（与 Java IdType.ASSIGN_ID 一致）。已上线库执行一次；新库以 schema_v1.sql 为准可跳过。
SET NAMES utf8mb4;

ALTER TABLE sec_user_account DROP INDEX uk_sec_user_username;

ALTER TABLE sec_user_account
  CHANGE COLUMN username login_name VARCHAR(128) NOT NULL COMMENT '登录名全局唯一，与昵称展示语义分离';

ALTER TABLE sec_user_account
  ADD UNIQUE KEY uk_sec_user_login_name (login_name);

ALTER TABLE sec_user_account
  MODIFY COLUMN id BIGINT NOT NULL COMMENT '自然人账号主键（雪花，非库自增）';
