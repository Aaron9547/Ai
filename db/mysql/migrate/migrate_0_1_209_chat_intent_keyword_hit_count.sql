-- 0.1.209：意图关键词命中统计；已上线库执行一次；新库以 schema_v1.sql 为准可跳过

ALTER TABLE chat_intent_keyword
  ADD COLUMN hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '配置关键词命中进入意图 SSE 的累计次数' AFTER sort_order;
