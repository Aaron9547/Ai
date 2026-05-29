-- 今日智能洞察 / 每日热点：检索词加入本日·昨日与地域占位；已建库须手工执行
-- 新库请同步 schema_v1.sql 平台种子段

UPDATE prompt_template
SET
    content = '${region_phrase}中国 科技 财经 教育 社会 校园 热点资讯 ${today} 今日 ${yesterday} 昨日 最新',
    version = version + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE tenant_id = 0
  AND prompt_code = 'daily_recommend_search_query'
  AND locale = '*';

UPDATE prompt_template
SET
    content = '${region_phrase}今日${today} 昨日${yesterday} 最新资讯 热点新闻 用户兴趣：${profile_excerpt}',
    version = version + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE tenant_id = 0
  AND prompt_code = 'daily_recommend_search_query_profile'
  AND locale = '*';

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'daily_recommend_search_query_yesterday', 'QUERY', 'WEB', '*', '${region_phrase}中国 科技 财经 教育 社会 校园 ${yesterday} 昨日 热点 补充', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'daily_recommend_search_query_yesterday_profile', 'QUERY', 'WEB', '*', '${region_phrase}${yesterday} 昨日 热点资讯 补充 用户兴趣：${profile_excerpt}', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

UPDATE prompt_template
SET
    content = '中国 网络与社会热点 科技 财经 文化 ${today} 今日 ${yesterday} 昨日 最新',
    version = version + 1,
    updated_at = UTC_TIMESTAMP(3)
WHERE tenant_id = 0
  AND prompt_code = 'starter_hot_search_query'
  AND locale = '*';
