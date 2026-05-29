-- 联网检索架构纠偏（已建库须手工执行）：
-- 1) 保留 WEB_SEARCH_GROUNDING_MODEL_ID 为火山 Ark（不变）
-- 2) 默认启用国内可直连的百度新闻（无配置行时插入；境外源请在 Shell 按需勾选）
-- 3) 清空已废弃的 WEB_SEARCH_GROUNDING_MODEL_IDS_JSON

INSERT INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.tenant_id,
       'WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON',
       '["BAIDU_NEWS_HTML"]',
       NOW(3),
       NOW(3)
FROM ten_runtime_setting t
WHERE t.setting_key = 'WEB_SEARCH_GROUNDING_MODEL_ID'
  AND TRIM(IFNULL(t.value_text, '')) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM ten_runtime_setting x
      WHERE x.tenant_id = t.tenant_id
        AND x.setting_key = 'WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON'
        AND TRIM(IFNULL(x.value_text, '')) NOT IN ('', '[]')
  );

INSERT INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT DISTINCT t.tenant_id,
       'WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON',
       '["BAIDU_NEWS_HTML"]',
       NOW(3),
       NOW(3)
FROM ten_runtime_setting t
WHERE t.setting_key = 'WEB_SEARCH_GROUNDING_MODEL_IDS_JSON'
  AND TRIM(IFNULL(t.value_text, '')) NOT IN ('', '[]')
  AND NOT EXISTS (
      SELECT 1
      FROM ten_runtime_setting x
      WHERE x.tenant_id = t.tenant_id
        AND x.setting_key = 'WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON'
        AND TRIM(IFNULL(x.value_text, '')) NOT IN ('', '[]')
  );

UPDATE ten_runtime_setting
SET value_text = '[]',
    updated_at = NOW(3)
WHERE setting_key = 'WEB_SEARCH_GROUNDING_MODEL_IDS_JSON'
  AND TRIM(IFNULL(value_text, '')) NOT IN ('', '[]');
