-- 0.1.247：对话页「今日 AI 推荐」按主体每日缓存
-- 已建库须手工执行；新库若 schema_v1.sql 已含 chat_user_daily_recommend 可只补网关 INSERT IGNORE 段

CREATE TABLE IF NOT EXISTS chat_user_daily_recommend (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  subject_key VARCHAR(96) NOT NULL COMMENT '画像主体 u:{userId} 或 d:{deviceId}',
  recommend_date DATE NOT NULL COMMENT '北京自然日',
  status VARCHAR(32) NOT NULL,
  items_json MEDIUMTEXT NULL COMMENT '推荐卡片 JSON 数组',
  error_message VARCHAR(512) NULL,
  retry_used TINYINT NOT NULL DEFAULT 0 COMMENT '当日失败后是否已重试 0/1',
  fetched_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cudr_tenant_subject_date (tenant_id, subject_key, recommend_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日个性化资讯推荐';

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at) VALUES
('/open/v1/chat/daily-recommend', 'GET', 'C端今日个性化资讯推荐', NULL, 1, 2108, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/daily-recommend/retry', 'POST', 'C端今日推荐失败重试', NULL, 1, 2109, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/daily-recommend/regenerate-on-login', 'POST', 'C端登录后刷新画像推荐', NULL, 1, 2110, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/daily-recommend/click', 'POST', 'C端画像推荐资讯点击埋点', NULL, 1, 2111, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
