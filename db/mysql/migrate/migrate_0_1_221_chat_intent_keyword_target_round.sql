-- 意图关键词可选目标轮次（处理器内枚举名，如 DOC/PLAN）；为空时由处理器按 keyword_kind 推断。
ALTER TABLE chat_intent_keyword
  ADD COLUMN target_round VARCHAR(32) NULL COMMENT '可选；多轮流处理器轮次名，与 ChatIntentKeywordKind 并列使用' AFTER keyword_kind;
