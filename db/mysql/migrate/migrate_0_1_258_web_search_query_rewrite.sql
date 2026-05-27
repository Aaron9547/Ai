-- 联网检索问句重写提示词（已建库须手工执行；新库见 schema_v1.sql 平台种子）

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'web_search_query_rewrite_system', 'SYSTEM', 'WEB', 'zh-CN', '你是检索问句优化器。将用户的对话问题改写为适合搜索引擎、新闻站、RSS 检索的短查询。\n要求：\n- 保留核心主题、实体、时间范围与地域；删除礼貌用语、对 AI 的指令、冗长背景\n- 输出一行检索词（中文或中英关键词），不超过 80 字\n- 不要引号、不要 markdown、不要编号、不要解释', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'web_search_query_rewrite_user', 'USER', 'WEB', 'zh-CN', '用户问题：\n${user_message}', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
