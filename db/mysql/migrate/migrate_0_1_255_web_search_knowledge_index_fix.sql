-- 0.1.255 修复：已执行旧版 migrate（含 uk_csp_tenant_web_norm）失败或仅缺 hash/索引时按需执行
-- 1) 若唯一索引误建成功：先 DROP INDEX uk_csp_tenant_web_norm;
-- 2) 若尚无 query_norm_hash：执行下列 ADD COLUMN + CREATE INDEX

ALTER TABLE chat_starter_prompt
  ADD COLUMN query_norm_hash CHAR(64) NULL COMMENT 'SHA256(规范化问句)，索引用' AFTER query_normalized;

CREATE INDEX idx_csp_web_knowledge_lookup
  ON chat_starter_prompt (tenant_id, query_norm_hash, updated_at);

-- 可选：为已有 WEB_KNOWLEDGE 行回填 hash（应用写入新行时会自动带 hash）
-- UPDATE chat_starter_prompt SET query_norm_hash = LOWER(SHA2(query_normalized, 256))
--   WHERE scene = 'WEB_KNOWLEDGE' AND query_normalized IS NOT NULL AND query_norm_hash IS NULL;
