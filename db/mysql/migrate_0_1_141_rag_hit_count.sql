-- 0.1.141：RAG 文档/分片命中统计（与 schema_v1 对齐；已建库环境按需执行）
ALTER TABLE rag_document ADD COLUMN hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '对话 RAG 召回命中累计次数（按分片命中计次汇总到文档）' AFTER uploaded_by_user_id;
ALTER TABLE rag_chunk ADD COLUMN hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '对话 RAG 召回命中该分片的累计次数' AFTER retrieval_enabled;
