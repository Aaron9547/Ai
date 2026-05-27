-- 0.1.252 补救：已执行 migrate_0_1_252 前半段且 email 为 VARCHAR(255) 时，建 uk_sec_user_email 报 Error 1071 后执行本脚本一次。
-- 若 uk_sec_user_email 已存在可跳过。

ALTER TABLE sec_user_account
  MODIFY COLUMN email VARCHAR(191) NULL COMMENT '绑定邮箱（小写规范化），唯一；191 兼容 utf8mb4 UNIQUE 767 字节上限';

-- 以下索引若已存在会报错，按需注释已成功的行
ALTER TABLE sec_user_account ADD UNIQUE KEY uk_sec_user_account_no (account_no);
ALTER TABLE sec_user_account ADD UNIQUE KEY uk_sec_user_email (email);
ALTER TABLE sec_user_account ADD UNIQUE KEY uk_sec_user_phone (phone);
