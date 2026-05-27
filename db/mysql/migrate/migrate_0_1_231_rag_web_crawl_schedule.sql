-- 0.1.231：网页爬取已入库 URL 追踪 + 一次性本地规则站点爬取 API（定时配置见 0.1.233 ten_scheduled_task）

CREATE TABLE IF NOT EXISTS rag_web_crawl_url_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  schedule_id BIGINT NOT NULL COMMENT '关联 ten_scheduled_task.id（历史列名 schedule_id）',
  url VARCHAR(768) NOT NULL,
  document_id BIGINT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_rag_wcui_schedule (tenant_id, schedule_id, deleted),
  KEY idx_rag_wcui_url (tenant_id, url(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网页爬取已入库 URL 子项';

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/rag-kbs/*/*/web-crawl/local', 'POST', '本地规则站点爬取（一次性）', NULL, 1, 4250, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
