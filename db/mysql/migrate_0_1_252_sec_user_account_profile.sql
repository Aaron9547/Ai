-- 0.1.252：sec_user_account 正式账号档案（account_no / email / phone / registration_channel）
-- 已建库须手工执行本脚本后再重启应用。

ALTER TABLE sec_user_account
  ADD COLUMN account_no VARCHAR(32) NULL COMMENT '对外账号编号，全局唯一' AFTER id,
  ADD COLUMN email VARCHAR(191) NULL COMMENT '绑定邮箱（小写规范化），唯一；191 兼容 utf8mb4 UNIQUE 767 字节上限' AFTER login_name,
  ADD COLUMN phone VARCHAR(32) NULL COMMENT '绑定手机号（规范化），唯一' AFTER email,
  ADD COLUMN registration_channel VARCHAR(32) NOT NULL DEFAULT 'USERNAME' COMMENT 'UserRegistrationChannel 枚举名' AFTER phone,
  ADD COLUMN registered_at DATETIME(3) NULL COMMENT '注册完成时间 UTC' AFTER registration_channel;

UPDATE sec_user_account
SET
  account_no = CONCAT('U', UPPER(SUBSTRING(SHA1(CONCAT('acc', CAST(id AS CHAR))), 1, 12))),
  email = CASE
    WHEN login_name LIKE '%@%.%' THEN LOWER(TRIM(login_name))
    ELSE NULL
  END,
  registration_channel = CASE
    WHEN login_name = 'admin' THEN 'ADMIN'
    WHEN login_name LIKE '%@%.%' THEN 'EMAIL'
    ELSE 'USERNAME'
  END,
  registered_at = COALESCE(created_at, UTC_TIMESTAMP(3))
WHERE account_no IS NULL OR account_no = '';

ALTER TABLE sec_user_account
  MODIFY COLUMN account_no VARCHAR(32) NOT NULL COMMENT '对外账号编号，全局唯一',
  MODIFY COLUMN registered_at DATETIME(3) NOT NULL COMMENT '注册完成时间 UTC';

-- 若列已按 VARCHAR(255) 添加导致索引失败，先收紧 email 再建唯一索引
ALTER TABLE sec_user_account
  MODIFY COLUMN email VARCHAR(191) NULL COMMENT '绑定邮箱（小写规范化），唯一；191 兼容 utf8mb4 UNIQUE 767 字节上限';

ALTER TABLE sec_user_account
  ADD UNIQUE KEY uk_sec_user_account_no (account_no),
  ADD UNIQUE KEY uk_sec_user_email (email),
  ADD UNIQUE KEY uk_sec_user_phone (phone);
