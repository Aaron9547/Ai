-- 已上线库：将 gw_cors_allowed_origin.origin 收紧为 VARCHAR(191)，使 uk_gw_cors_origin 在 utf8mb4 + InnoDB 767 字节索引上限下可创建。
-- 适用：曾用 origin VARCHAR(512) 建表成功（如较新 MySQL），需在旧环境对齐；或 UNIQUE 建失败仅改了列未改索引的库。
-- 若 migrate_0_1_69 已按 VARCHAR(191) 建表成功，可跳过。

SET NAMES utf8mb4;

ALTER TABLE gw_cors_allowed_origin
  MODIFY COLUMN origin VARCHAR(191) NOT NULL COMMENT '完整 Origin（scheme+host+port）；最长 191 以兼容 InnoDB utf8mb4 UNIQUE 索引 767 字节上限';
