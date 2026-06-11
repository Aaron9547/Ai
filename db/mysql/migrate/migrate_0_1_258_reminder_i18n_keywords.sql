-- 一句话提醒：补充英文意图关键词（已建库手工执行）
SET @def_tid := (SELECT id FROM sys_tenant WHERE code = 'default' LIMIT 1);
SET @rem_iid := (SELECT id FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'one_sentence_reminder' LIMIT 1);

INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @rem_iid, 'remind me', 0, 1, 12, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @rem_iid IS NOT NULL;

INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @rem_iid, 'cancel reminder', 2, 1, 22, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @rem_iid IS NOT NULL;
