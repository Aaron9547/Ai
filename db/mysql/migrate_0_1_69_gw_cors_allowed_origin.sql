-- 已上线库增量：CORS 白名单表 + 本地开发默认来源（与 schema_v1.sql 一致）
-- 若已整库执行过更新后的 schema_v1.sql，可跳过本文件。
-- origin 使用 VARCHAR(191)：utf8mb4 下 UNIQUE 索引须 ≤767 字节（191×4=764），避免 Error 1071。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS gw_cors_allowed_origin (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  origin VARCHAR(191) NOT NULL COMMENT '完整 Origin（scheme+host+port）；最长 191 以兼容 InnoDB utf8mb4 唯一索引字节上限',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'ToggleState：0=OFF 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  remark VARCHAR(512) NULL COMMENT '说明',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_gw_cors_origin (origin),
  KEY idx_gw_cors_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CORS 浏览器访问来源白名单';

SET FOREIGN_KEY_CHECKS = 1;

INSERT IGNORE INTO gw_cors_allowed_origin (origin, enabled, sort_order, remark, created_at, updated_at) VALUES
('http://localhost:5173', 1, 10, '用户端 dev', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://localhost:5174', 1, 11, '管理端 dev（可选端口）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://localhost:5176', 1, 12, '管理端 dev（默认 vite）', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://127.0.0.1:5173', 1, 20, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://127.0.0.1:5174', 1, 21, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('http://127.0.0.1:5176', 1, 22, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
