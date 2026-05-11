-- 0.1.168：job_task.rag_kb_id 冗余 RAG 知识库 id，供管理端按库筛选（不依赖 JSON_EXTRACT）。
ALTER TABLE job_task
  ADD COLUMN rag_kb_id BIGINT NULL COMMENT 'RAG 知识库 id（与 payload_json.kbId 冗余；管理端按库筛选，避免依赖 JSON 函数）' AFTER payload_json;

CREATE INDEX idx_job_task_tenant_rag_kb ON job_task (tenant_id, rag_kb_id);
