-- 0.1.205：对话意图定义与关键词、管理端菜单 CHAT_INTENTS、网关限流清单；已上线库按需执行；空库以 schema_v1.sql 为准

CREATE TABLE IF NOT EXISTS chat_intent_definition (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  code VARCHAR(64) NOT NULL COMMENT '租户内唯一意图编码（英文 snake）',
  display_name VARCHAR(128) NOT NULL COMMENT '展示名',
  description VARCHAR(512) NULL COMMENT '说明',
  handler_kind TINYINT NOT NULL DEFAULT 0 COMMENT 'ChatIntentHandlerKind：0=TRAVEL_REIMBURSEMENT',
  enabled TINYINT NOT NULL DEFAULT 0 COMMENT '0=OFF 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同租户多条意图时匹配优先级（大者优先尝试）',
  extra_config_json LONGTEXT NULL COMMENT '处理器扩展 JSON',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_intent_def_tenant_code (tenant_id, code),
  KEY idx_chat_intent_def_tenant_en (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话意图定义';

CREATE TABLE IF NOT EXISTS chat_intent_keyword (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户隔离键',
  intent_id BIGINT NOT NULL COMMENT 'chat_intent_definition.id',
  phrase VARCHAR(255) NOT NULL COMMENT '触发短语',
  keyword_kind TINYINT NOT NULL DEFAULT 0 COMMENT 'ChatIntentKeywordKind：0=TRIGGER 1=PLAN_CONTINUE',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0=OFF 1=ON',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_intent_kw_intent_phrase (intent_id, phrase),
  KEY idx_chat_intent_kw_tenant_intent (tenant_id, intent_id),
  KEY idx_chat_intent_kw_tenant_phrase (tenant_id, phrase(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意图触发关键词';

INSERT IGNORE INTO lnk_tenant_admin_menu (tenant_id, menu_code, created_at)
SELECT t.id, 'CHAT_INTENTS', UTC_TIMESTAMP(3) FROM sys_tenant t;

INSERT IGNORE INTO lnk_tenant_user_admin_menu (tenant_id, user_id, menu_code, created_at)
SELECT t.id, u.id, 'CHAT_INTENTS', UTC_TIMESTAMP(3)
FROM sys_tenant t
INNER JOIN sec_user_account u ON u.login_name = 'admin';

INSERT IGNORE INTO sys_admin_menu_item (menu_code, title_zh, route_path, sort_order, enabled, created_at, updated_at) VALUES
('CHAT_INTENTS', '意图识别', '/chat/intents', 125, 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

SET @def_tid := 1;
INSERT INTO chat_intent_definition (tenant_id, code, display_name, description, handler_kind, enabled, sort_order, extra_config_json, created_at, updated_at)
SELECT @def_tid, 'travel_reimbursement', '出差报销', '分阶段出差办理；扩展字段 extra_config_json 可配置 Coze 等（见 PROJECT.md）。', 0, 0, 100, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'travel_reimbursement');

SET @travel_iid := (SELECT id FROM chat_intent_definition WHERE tenant_id = @def_tid AND code = 'travel_reimbursement' LIMIT 1);

INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '出差报销', 0, 1, 10, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '差旅报销', 0, 1, 11, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '报销差旅费', 0, 1, 12, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '继续', 1, 1, 20, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '下一步', 1, 1, 21, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;
INSERT IGNORE INTO chat_intent_keyword (tenant_id, intent_id, phrase, keyword_kind, enabled, sort_order, created_at, updated_at)
SELECT @def_tid, @travel_iid, '安排行程', 1, 1, 22, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM DUAL WHERE @travel_iid IS NOT NULL;

INSERT IGNORE INTO gw_api_rate_limit_rule (tenant_id, path_pattern, http_method, requests_per_minute, enabled, remark, created_at, updated_at) VALUES
(NULL, '/api/v1/admin/chat/intents', 'GET', 120, 0, '意图列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents', 'POST', 30, 0, '新增意图', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents/*', 'PUT', 30, 0, '更新意图', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents/*', 'DELETE', 20, 0, '删除意图', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents/*/keywords', 'GET', 120, 0, '意图关键词列表', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents/*/keywords', 'POST', 30, 0, '新增意图关键词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents/*/keywords/*', 'PUT', 30, 0, '更新意图关键词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(NULL, '/api/v1/admin/chat/intents/*/keywords/*', 'DELETE', 20, 0, '删除意图关键词', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
