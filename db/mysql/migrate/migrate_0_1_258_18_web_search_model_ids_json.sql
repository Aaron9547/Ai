-- 联网检索源：恢复 WEB_SEARCH_GROUNDING_MODEL_IDS_JSON 多模型列表（已建库须手工执行）
-- 将已有 WEB_SEARCH_GROUNDING_MODEL_ID 单键回填为 ["id"]，与 Shell「联网检索源」多选对齐。

INSERT INTO ten_runtime_setting (tenant_id, setting_key, value_text, created_at, updated_at)
SELECT t.tenant_id,
       'WEB_SEARCH_GROUNDING_MODEL_IDS_JSON',
       CONCAT('["', TRIM(t.value_text), '"]'),
       NOW(3),
       NOW(3)
FROM ten_runtime_setting t
WHERE t.setting_key = 'WEB_SEARCH_GROUNDING_MODEL_ID'
  AND TRIM(IFNULL(t.value_text, '')) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM ten_runtime_setting x
      WHERE x.tenant_id = t.tenant_id
        AND x.setting_key = 'WEB_SEARCH_GROUNDING_MODEL_IDS_JSON'
        AND TRIM(IFNULL(x.value_text, '')) NOT IN ('', '[]')
  );
