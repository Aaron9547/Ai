-- 今日智能洞察：检索词与结构化提示词 v2（本日日期 + 画像关键词优先，去宽泛类目词）
-- 已建库须手工执行；新库请同步 schema_v1.sql
-- 说明：勿对 prompt_template 做 version+1 批量 UPDATE（uk_prompt_template_scope 含 version，多版本行会 1062）
-- 本脚本仅更新各 prompt_code 当前最高 version 行 content；执行后建议重启后端或等待 prompt Redis 缓存过期

-- 同 scope 仅保留最高 version 为 enabled（修复历史 migrate 叠版本）
UPDATE prompt_template p
INNER JOIN (
  SELECT tenant_id, prompt_code, locale, MAX(version) AS max_ver
  FROM prompt_template
  WHERE tenant_id = 0
    AND prompt_code IN (
      'daily_recommend_structure',
      'daily_recommend_search_query',
      'daily_recommend_search_query_profile',
      'daily_recommend_search_query_yesterday',
      'daily_recommend_search_query_yesterday_profile'
    )
  GROUP BY tenant_id, prompt_code, locale
) t ON p.tenant_id = t.tenant_id AND p.prompt_code = t.prompt_code AND p.locale = t.locale
SET p.enabled = 0, p.updated_at = UTC_TIMESTAMP(3)
WHERE p.version < t.max_ver;

UPDATE prompt_template p
INNER JOIN (
  SELECT id FROM prompt_template
  WHERE tenant_id = 0 AND prompt_code = 'daily_recommend_structure' AND locale = 'zh-CN'
  ORDER BY version DESC
  LIMIT 1
) latest ON p.id = latest.id
SET p.content = '你是资讯推荐编辑。根据联网检索摘要与用户画像，为**当前这位用户**输出 ${today}（${today_label}）的个性化资讯卡片列表。
只输出 JSON 数组，不要 markdown，不要解释。每项字段：
tag（领域标签，2～8字）、title（标题，12～48字）、summary（摘要，24～120字）、
source（来源媒体名）、date（发布日期 yyyy-MM-dd，不得晚于 ${today}；须来自检索摘要中的发布时间，无法判断时写 ${today}）、
url（可点击链接，须 http/https，且必须从【联网引用列表】中原样选取，禁止编造域名）。
共 5～8 条，内容须为 ${today} 前后真实资讯，禁止编造未来日期或虚构事件。
若提供【用户画像与记忆】：至少 4 条须与用户兴趣、专业、近期对话或点击偏好直接相关；不同用户的内容组合应有明显差异，勿用与用户无关的泛化热点凑数。
若无画像：输出 ${today} 当日中国综合热点资讯。
示例：[{"tag":"科技","title":"…","summary":"…","source":"新华网","date":"${today}","url":"https://…"}]',
    p.updated_at = UTC_TIMESTAMP(3);

UPDATE prompt_template p
INNER JOIN (
  SELECT id FROM prompt_template
  WHERE tenant_id = 0 AND prompt_code = 'daily_recommend_search_query' AND locale = '*'
  ORDER BY version DESC
  LIMIT 1
) latest ON p.id = latest.id
SET p.content = '${region_phrase}${today} ${today_label} 中国 热点新闻 社会 今日最新',
    p.updated_at = UTC_TIMESTAMP(3);

UPDATE prompt_template p
INNER JOIN (
  SELECT id FROM prompt_template
  WHERE tenant_id = 0 AND prompt_code = 'daily_recommend_search_query_profile' AND locale = '*'
  ORDER BY version DESC
  LIMIT 1
) latest ON p.id = latest.id
SET p.content = '${profile_excerpt} ${today} ${today_label} 最新资讯 热点 个性化',
    p.updated_at = UTC_TIMESTAMP(3);

UPDATE prompt_template p
INNER JOIN (
  SELECT id FROM prompt_template
  WHERE tenant_id = 0 AND prompt_code = 'daily_recommend_search_query_yesterday' AND locale = '*'
  ORDER BY version DESC
  LIMIT 1
) latest ON p.id = latest.id
SET p.content = '${region_phrase}${yesterday} 昨日 中国 热点 补充',
    p.updated_at = UTC_TIMESTAMP(3);

UPDATE prompt_template p
INNER JOIN (
  SELECT id FROM prompt_template
  WHERE tenant_id = 0 AND prompt_code = 'daily_recommend_search_query_yesterday_profile' AND locale = '*'
  ORDER BY version DESC
  LIMIT 1
) latest ON p.id = latest.id
SET p.content = '${profile_excerpt} ${yesterday} 昨日 补充 热点',
    p.updated_at = UTC_TIMESTAMP(3);
