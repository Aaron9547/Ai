-- 0.1.235：移除已废弃的知识库内「网页爬取定时任务」专用 API（已迁至 /admin/scheduled-tasks）

SET NAMES utf8mb4;

DELETE FROM gw_api_endpoint
WHERE path_pattern IN (
  '/api/v1/admin/rag-kbs/*/web-crawl/meta',
  '/api/v1/admin/rag-kbs/*/*/web-crawl/schedules',
  '/api/v1/admin/rag-kbs/*/*/web-crawl/schedules/*',
  '/api/v1/admin/rag-kbs/*/*/web-crawl/schedules/*/run'
)
OR path_pattern LIKE '/api/v1/admin/rag-kbs/%/web-crawl/schedules%';

-- 若 0.1.231 旧版曾创建 rag_web_crawl_schedule 且未跑 0.1.233，此处兜底删除
DROP TABLE IF EXISTS rag_web_crawl_schedule;
