-- 0.1.85：画像表 ten_profile_tag（与 schema_v1 对齐）；已含该表的空库可跳过。
CREATE TABLE IF NOT EXISTS ten_profile_tag (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  subject_key VARCHAR(96) NOT NULL COMMENT 'u:{userId} 或 d:{deviceId}',
  tag_code VARCHAR(64) NOT NULL COMMENT 'ProfileTagCode 存储值',
  tag_value VARCHAR(1024) NOT NULL COMMENT '标签值',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_profile_tag (tenant_id, subject_key, tag_code),
  KEY idx_ten_profile_tenant_subject (tenant_id, subject_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户内用户/设备画像标签';
