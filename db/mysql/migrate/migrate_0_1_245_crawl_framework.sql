-- 0.1.245：站点爬取框架 — crawl_run / crawl_url_queue（持久化发现与抓取队列）
-- 已建库须手工执行本脚本；新库以 schema_v1.sql 为准可跳过。

CREATE TABLE IF NOT EXISTS crawl_run (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  kb_id BIGINT NOT NULL,
  site_id BIGINT NULL COMMENT 'rag_web_crawl_site.id，一次性爬取可为空',
  base_url VARCHAR(2048) NOT NULL,
  sync_mode VARCHAR(32) NOT NULL,
  preset VARCHAR(32) NOT NULL COMMENT 'CONSERVATIVE/BALANCED/AGGRESSIVE/CUSTOM',
  policy_summary VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  stats_json LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_crawl_run_tenant_kb (tenant_id, kb_id),
  KEY idx_crawl_run_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_url_queue (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  url VARCHAR(2048) NOT NULL,
  url_norm VARCHAR(2048) NOT NULL,
  queue_role VARCHAR(16) NOT NULL COMMENT 'EXPLORE|ARTICLE',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  score INT NOT NULL DEFAULT 0,
  sources_json VARCHAR(2048) NULL,
  error_code VARCHAR(64) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  etag VARCHAR(256) NULL,
  last_modified VARCHAR(128) NULL,
  document_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_crawl_url_queue_run_status (run_id, status),
  KEY idx_crawl_url_queue_tenant (tenant_id),
  -- utf8mb4 下单索引前缀 ≤191 字符（767 字节上限），与 rag_web_crawl_url_item.url(191) 一致
  UNIQUE KEY uk_crawl_url_queue_run_norm (run_id, url_norm(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
