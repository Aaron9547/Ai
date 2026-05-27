-- 0.1.257-SNAPSHOT：LLM 提示词模板（prompt_template）+ 平台种子 + 管理端菜单/API
-- 已建库须手工执行；幂等。新库以 schema_v1.sql 为准已含表与平台种子，可只执行菜单与 gw 段。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=平台默认；>0=租户覆盖',
  prompt_code VARCHAR(64) NOT NULL COMMENT '全局逻辑键，如 follow_up_system / websearch_summary_header',
  prompt_kind VARCHAR(32) NOT NULL COMMENT 'PromptTemplateKind：SYSTEM|USER|FRAGMENT|QUERY',
  domain VARCHAR(32) NOT NULL COMMENT 'PromptTemplateDomain：CHAT|MEMORY|RAG|WEB|PLANET|STARTER|GUARD',
  locale VARCHAR(16) NOT NULL DEFAULT '*' COMMENT 'zh-CN|en-US|*',
  content LONGTEXT NOT NULL COMMENT '提示词正文；USER 模板可含 ${var}',
  variables_schema_json MEDIUMTEXT NULL COMMENT '占位符说明 JSON',
  version INT NOT NULL DEFAULT 1 COMMENT '同 code 多版本；解析取 enabled 最高 version',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  remark VARCHAR(512) NULL COMMENT '运营备注',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同前缀 FRAGMENT 排序',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_prompt_template_scope (tenant_id, prompt_code, locale, version),
  KEY idx_prompt_template_code (prompt_code),
  KEY idx_prompt_template_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 提示词模板（平台默认 + 租户覆盖）';

-- 平台默认种子（与 PromptTemplateBuiltinCatalog 一致；维护时请同步 schema_v1 同段）

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('PROMPT_TEMPLATES', '提示词工程', '/prompt/templates', 22, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

UPDATE sys_admin_menu_item SET title_zh = '提示词工程', route_path = '/prompt/templates', updated_at = UTC_TIMESTAMP(3)
WHERE menu_code = 'PROMPT_TEMPLATES';

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'PROMPT_TEMPLATES', UTC_TIMESTAMP(3) FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'PROMPT_TEMPLATES', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/prompt-templates', 'GET', '管理端提示词模板列表', NULL, 1, 4380, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/prompt-templates', 'POST', '管理端新建提示词模板', NULL, 1, 4381, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/prompt-templates/*', 'PUT', '管理端更新提示词模板', NULL, 1, 4382, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/prompt-templates/*', 'DELETE', '管理端删除提示词模板', NULL, 1, 4383, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/prompt-templates/cache-stats', 'GET', '管理端提示词缓存统计', NULL, 1, 4384, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/prompt-templates/cache/evict', 'POST', '管理端提示词缓存失效', NULL, 1, 4385, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'follow_up_system', 'SYSTEM', 'STARTER', 'zh-CN', '根据用户问题与助手回复，生成 2～3 条用户可能继续追问的短句。
只输出 JSON 数组，不要 markdown。每项中文 8～36 字，与上文强相关、不重复。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'daily_recommend_structure', 'SYSTEM', 'CHAT', 'zh-CN', '你是资讯推荐编辑。根据联网检索摘要与用户画像，输出今日个性化资讯卡片列表。
只输出 JSON 数组，不要 markdown，不要解释。每项字段：
tag（领域标签，2～8字）、title（标题，12～48字）、summary（摘要，24～120字）、
source（来源媒体名）、date（发布日期 yyyy-MM-dd，未知可写今日）、            url（可点击链接，须 http/https，且必须从【联网引用列表】中原样选取，禁止编造域名）。
共 5～8 条，内容不重复、与画像相关；若无画像则输出通用热点资讯。
示例：[{"tag":"科技","title":"…","summary":"…","source":"新华网","date":"2026-05-21","url":"https://…"}]', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'starter_hot_topic_structure', 'SYSTEM', 'STARTER', 'zh-CN', '你是推荐问句编辑。根据用户提供的联网检索摘要，输出适合 AI 对话开场白的短问题。
只输出 JSON 数组，不要 markdown，不要解释。每项为中文问句，长度 8～36 字，共 8～12 条。
问句应具体、可点击、避免重复。示例：["AIGC 最近有哪些新应用？","如何写一份周报模板？"]', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'starter_hot_search_query', 'QUERY', 'WEB', '*', '今日中国网络与社会热点新闻 科技 财经 文化 2026 最新', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'turn_digest_system', 'SYSTEM', 'CHAT', 'zh-CN', '你是对话归档助手。根据「用户问题」和「助手完整回复」，只输出严格 JSON（不要 markdown 代码块、不要多余说明），格式：
{"contentSummary":"...","conversationTitle":"..."}

约束：
- contentSummary：中文，1～3 句，抓住要点，供后续轮次作上下文压缩占位，不超过 420 字。
- conversationTitle：若输入中 needTitle 为 true，则填写不超过 24 字的简短会话标题（概括主题，不要引号与换行）；若 needTitle 为 false，必须填空字符串 ""。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'memory_abstract_system', 'SYSTEM', 'MEMORY', 'zh-CN', '你是「记忆管理员」后台服务，负责把对话-derived 的片段整理成**一份 JSON 对象**（不要 Markdown、不要解释性正文）。
目标：维护用户长期陪伴所需的**稳定语义**与**可检索要点**，并区分：
- 工作记忆/近期情景：短周期内仍可能变化的事实；
- 稳定事实：跨多轮仍成立、值得长期记住的偏好/身份/计划摘要；
- 画像增量 profile_delta：对结构化用户画像的可合并增量（键冲突时以**新对话为准**覆盖旧值）；
- 遗忘建议 forget_candidates：明显琐碎、无复用价值的一两句话（可为空数组）。

硬性规则：
1) **只输出 JSON**，顶层须为对象，且必须包含字段 schema_version（固定为 memory_abstract_v2）。
2) 不要编造用户未表达的内容；不确定的写入 stable_facts 时降低确信度或省略。
3) 若新信息与旧 abstract JSON 冲突，以**当前 chunk 转写**为准，并在 profile_delta 或 stable_facts 中体现修正。
4) 语言与用户输入一致（中文为主则中文输出）。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'memory_abstract_user', 'USER', 'MEMORY', '*', 'subject_key=${subject_key}
trigger=${trigger}

【旧 abstract JSON（可能为空）】
${old_abstract_json}

【最近双线记忆片段（时间顺序从早到新；USER/ASSISTANT 标注）】
${chunk_transcript}

请输出 JSON，字段要求如下：
{
  "schema_version": "memory_abstract_v2",
  "working_summary": "字符串：近期情景一句话",
  "episodic_hooks": ["可选：仍活跃的短期话题钩子"],
  "stable_facts": ["可选：长期稳定事实，短句"],
  "profile_delta": { "任意键": "任意值" },
  "forget_candidates": ["可选：建议丢弃的琐碎原句"],
  "merge_notes": "可选：给前台/模型的合并说明"
}', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'planet_ingest_system', 'SYSTEM', 'PLANET', 'zh-CN', '你是对话知识沉淀助手。根据本轮用户问题与助手回复，判断是否值得沉淀为一条「知识节点」。
只输出严格 JSON（不要 markdown），格式：
{"skip":true}
或
{"skip":false,"title":"不超过24字标题","summary":"1～3句摘要","topicTags":["主题分类","子标签1","子标签2"]}
topicTags 第一项为「知识星球」主题名（如：排序算法、Java、前端工程化），决定星系中的星球；第 2 项起为子标签（技术名、语言、算法名等，便于与历史节点关联）。
规则：
1. 若用户消息中给出【已有主题星球】，且本轮属于同一技术领域，topicTags[0] 必须与列表中某一项完全一致，勿为相近话题另造新名（如已有「排序算法」则勿写「Java排序」「算法」）。
2. 同一对话内的追问、换语言实现、对比、延伸（如「五种语言冒泡排序」接在「十大排序」后）应沉淀，skip 仅用于纯寒暄或完全无新信息的重复。
3. 子标签尽量包含能串联历史节点的关键词（如：排序、冒泡、Java、多语言）。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'planet_weekly_system', 'SYSTEM', 'PLANET', 'zh-CN', '你是个人成长教练。根据用户过去一周的对话知识节点与记忆摘要，生成本周成长方案。
只输出严格 JSON（不要 markdown）：
{"summary":"一句话总览","thinkDirections":["方向1"],"gapAreas":["不足1"],"bookRecommendations":[{"title":"书名","reason":"理由"}]}
thinkDirections 3～5 条；gapAreas 2～4 条；bookRecommendations 2～4 本。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'chat_assistant_persona', 'FRAGMENT', 'CHAT', 'zh-CN', '你是 Ai 中台助手。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'chat_assistant_persona', 'FRAGMENT', 'CHAT', 'en-US', 'You are the Ai platform assistant.', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'chat_language_directive', 'FRAGMENT', 'CHAT', 'zh-CN', '【回复语种】请使用简体中文回复。若用户明确要求使用其他语言，则按用户要求。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'chat_language_directive', 'FRAGMENT', 'CHAT', 'en-US', '【Response language】Reply in English. If the user explicitly asks for another language, follow the user.', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'profile_guard_header', 'FRAGMENT', 'GUARD', 'zh-CN', '【内部·跨会话参考·勿直接向用户暴露】', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'profile_guard_header', 'FRAGMENT', 'GUARD', 'en-US', '【Internal·cross-session context·do not expose to the user】', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'profile_guard_usage', 'FRAGMENT', 'GUARD', 'zh-CN', '【使用规则】以上内容来自其它会话/设备的后台记忆，不是用户在本聊天窗口内可见的历史。禁止在思考过程或正文中对用户宣称「您问过/说过多次」「您反复问过」「您之前问过」等，除非下方「历史对话」中已出现相同用户发言。${first_turn_extra}可静默利用记忆改善回答，勿向用户复述其中的统计或元信息。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'profile_guard_usage', 'FRAGMENT', 'GUARD', 'en-US', '【Usage rules】The block above is backend memory from other chats/devices, not what the user sees in this window. Do not claim in reasoning or the reply that the user asked or said something "many times", "again", or "before", unless the conversation history below already contains the same user message. ${first_turn_extra}You may use the memory silently to personalize; do not narrate its metadata.', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'profile_guard_first_turn_extra', 'FRAGMENT', 'GUARD', 'zh-CN', '当前窗口尚无历史轮次：默认按用户在本窗口**首次提问**对待，勿根据本块推断其曾反复提问。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'profile_guard_first_turn_extra', 'FRAGMENT', 'GUARD', 'en-US', 'This window has no prior turns: treat the latest user message as their first question in this chat unless history below shows otherwise.', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'rag_snippet_header', 'FRAGMENT', 'RAG', 'zh-CN', '可参考知识片段', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'rag_snippet_header_web_hint', 'FRAGMENT', 'RAG', 'zh-CN', '（若与当前问题无关请忽略，并优先依据联网检索结果作答）', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'websearch_summary_header', 'FRAGMENT', 'WEB', 'zh-CN', '【网络检索摘要】', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'websearch_citation_header', 'FRAGMENT', 'WEB', 'zh-CN', '【引用】', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'user_attachment_marker', 'FRAGMENT', 'CHAT', 'zh-CN', '【以下为用户上传文档摘要，请结合回答】', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'attachment_context_intro', 'FRAGMENT', 'CHAT', 'zh-CN', '用户上传了以下文档，正文已合并进 user 消息；请遵守引用边界，勿编造未出现的事实。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'follow_up_fallback_1', 'FRAGMENT', 'STARTER', 'zh-CN', '能再具体说说吗？', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'follow_up_fallback_2', 'FRAGMENT', 'STARTER', 'zh-CN', '还有其他需要注意的吗？', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'follow_up_fallback_3', 'FRAGMENT', 'STARTER', 'zh-CN', '请举一个实际例子', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'starter_empty_fallback_1', 'FRAGMENT', 'STARTER', 'zh-CN', '写一首关于春天的诗', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'starter_empty_fallback_2', 'FRAGMENT', 'STARTER', 'zh-CN', '用通俗语言解释量子纠缠', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'starter_empty_fallback_3', 'FRAGMENT', 'STARTER', 'zh-CN', '帮我生成一份周报模板', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
