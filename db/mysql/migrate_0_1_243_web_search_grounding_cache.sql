-- 联网检索 Redis 缓存默认策略（语义近邻 + 滚动 6h/24h/48h + 会话内复用 6h）。
-- 与 pom 0.1.243-SNAPSHOT 一致；已建库须手工执行。

INSERT IGNORE INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT
    t.id,
    'WEB_SEARCH_GROUNDING_CACHE_JSON',
    '{"enabled":true,"freshHours":6,"warmHours":24,"staleHours":48,"semanticEnabled":true,"similarityThreshold":0.92,"indexMaxEntries":200,"conversationReuseHours":6}',
    UTC_TIMESTAMP(3),
    UTC_TIMESTAMP(3)
FROM sys_tenant t;
