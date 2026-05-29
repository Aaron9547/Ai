-- 0.1.258-SNAPSHOT：知识星球周报 v2（progress ledger、学习者画像、荐书联网配置、提示词）
-- 已建库须手工执行；幂等。

ALTER TABLE ten_user_weekly_insight
  ADD COLUMN progress_ledger_json VARCHAR(2048) NULL
    COMMENT '周度进度压缩账本 JSON，供下周 Prompt 印证' AFTER plan_json;

CREATE TABLE IF NOT EXISTS ten_user_learner_profile (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  user_id BIGINT NOT NULL COMMENT 'sec_user_account.id',
  body_json LONGTEXT NULL COMMENT '学习者画像 JSON（roleOrStage/coreInterests/skillHints/learningGoals）',
  updated_week_start DATE NULL COMMENT '最近一次由周报 merge 的自然周周一',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ten_user_learner_profile (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人知识星球·学习者画像滚动快照';

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'KNOWLEDGE_PLANET_WEEKLY_BOOK_SEARCH_ENABLED', 'true', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.id, 'KNOWLEDGE_PLANET_WEEKLY_MIN_NODES', '2', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM sys_tenant t;

UPDATE prompt_template
SET content = '你是个人成长教练。根据用户本周对话知识节点、学习者画像、记忆要点、历史进度账本与（若有）联网书目摘要，生成本周成长方案并对照近几周是否进步。
只输出严格 JSON（不要 markdown）：
{
  "summary":"一句话总览",
  "inferredPersona":"1～2句：我们理解中的你",
  "evidenceTopics":["引用的主题星球或稳定事实"],
  "progressNotes":"相对近1～3周的进步/停滞/重复短板（1～3句）",
  "thinkDirections":["方向1"],
  "gapAreas":["不足1"],
  "bookRecommendations":[{"title":"书名","reason":"理由","source":"web_search|classic"}],
  "learnerProfileDelta":{"roleOrStage":"","coreInterests":[],"skillHints":{},"learningGoals":[]},
  "bookSearchQuery":""
}
thinkDirections 3～5 条；gapAreas 2～4 条；bookRecommendations 2～4 本。
若提供【联网书目摘要】，bookRecommendations 须优先从中选取并设 source=web_search；摘要不足时可补 source=classic 的公认经典，不得编造 url。
bookSearchQuery 填实际使用的检索词（无联网则空串）。',
    updated_at = UTC_TIMESTAMP(3)
WHERE tenant_id = 0
  AND prompt_code = 'planet_weekly_system'
  AND locale = 'zh-CN'
  AND prompt_kind = 'SYSTEM';

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, created_at, updated_at)
SELECT 0, 'planet_weekly_system', 'SYSTEM', 'PLANET', 'zh-CN',
'你是个人成长教练。根据用户本周对话知识节点、学习者画像、记忆要点、历史进度账本与（若有）联网书目摘要，生成本周成长方案并对照近几周是否进步。
只输出严格 JSON（不要 markdown）：
{
  "summary":"一句话总览",
  "inferredPersona":"1～2句：我们理解中的你",
  "evidenceTopics":["引用的主题星球或稳定事实"],
  "progressNotes":"相对近1～3周的进步/停滞/重复短板（1～3句）",
  "thinkDirections":["方向1"],
  "gapAreas":["不足1"],
  "bookRecommendations":[{"title":"书名","reason":"理由","source":"web_search|classic"}],
  "learnerProfileDelta":{"roleOrStage":"","coreInterests":[],"skillHints":{},"learningGoals":[]},
  "bookSearchQuery":""
}
thinkDirections 3～5 条；gapAreas 2～4 条；bookRecommendations 2～4 本。
若提供【联网书目摘要】，bookRecommendations 须优先从中选取并设 source=web_search；摘要不足时可补 source=classic 的公认经典，不得编造 url。
bookSearchQuery 填实际使用的检索词（无联网则空串）。',
UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM prompt_template
  WHERE tenant_id = 0 AND prompt_code = 'planet_weekly_system' AND locale = 'zh-CN' AND prompt_kind = 'SYSTEM'
);

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/open/v1/chat/knowledge-planet/weekly/feedback', 'POST', 'C端知识星球周报反馈', NULL, 1, 2115, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/open/v1/chat/knowledge-planet/learning-goal', 'PUT', 'C端知识星球学习目标', NULL, 1, 2116, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
