-- 0.1.255：对话联网检索沉淀为租户默认知识库（chat_starter_prompt scene=WEB_KNOWLEDGE）
-- 已建库须手工执行；新库若 schema_v1.sql 已含下列列可跳过
-- 注意：不对 query_normalized 建 UNIQUE（utf8mb4 超 767 字节上限，且同一问句需多版本容纳资讯更新）

ALTER TABLE chat_starter_prompt
  ADD COLUMN query_normalized VARCHAR(512) NULL COMMENT '规范化问句（展示与语义匹配）' AFTER batch_key,
  ADD COLUMN query_norm_hash CHAR(64) NULL COMMENT 'SHA256(规范化问句)，索引用' AFTER query_normalized,
  ADD COLUMN grounding_json MEDIUMTEXT NULL COMMENT '联网摘要与引用 JSON（scene=WEB_KNOWLEDGE）' AFTER query_norm_hash,
  ADD COLUMN hit_count INT NOT NULL DEFAULT 0 COMMENT '本地知识库命中次数' AFTER grounding_json;

CREATE INDEX idx_csp_web_knowledge_lookup
  ON chat_starter_prompt (tenant_id, query_norm_hash, updated_at);
