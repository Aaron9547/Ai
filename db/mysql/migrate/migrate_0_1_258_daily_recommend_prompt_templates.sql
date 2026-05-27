-- 今日智能洞察：检索词与结构化提示词迁入 prompt_template（与 PromptTemplateBuiltinCatalog 同集）
-- 已建库须手工执行；新库请同步 schema_v1.sql 平台种子段

UPDATE prompt_template
SET
    content = '你是资讯推荐编辑。根据联网检索摘要与用户画像，输出今日个性化资讯卡片列表。
只输出 JSON 数组，不要 markdown，不要解释。每项字段：
tag（领域标签，2～8字）、title（标题，12～48字）、summary（摘要，24～120字）、
source（来源媒体名）、date（发布日期 yyyy-MM-dd，不得晚于今日 ${today}；须来自检索摘要中的发布时间，无法判断时写 ${today}）、
url（可点击链接，须 http/https，且必须从【联网引用列表】中原样选取，禁止编造域名）。
共 5～8 条，内容须为近期真实资讯，禁止编造未来日期或虚构事件；若无画像则输出通用热点资讯。
示例：[{"tag":"科技","title":"…","summary":"…","source":"新华网","date":"${today}","url":"https://…"}]',
    version = version + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE tenant_id = 0
  AND prompt_code = 'daily_recommend_structure'
  AND locale = 'zh-CN';

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'daily_recommend_search_query', 'QUERY', 'WEB', '*', '今日中国 科技 财经 教育 社会 校园 热点资讯 最新 ${year}', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'daily_recommend_search_query_profile', 'QUERY', 'WEB', '*', '今日最新资讯 热点新闻 与以下用户兴趣相关：${profile_excerpt} ${year}', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
