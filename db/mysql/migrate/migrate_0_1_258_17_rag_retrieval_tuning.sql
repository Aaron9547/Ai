-- RAG 检索调优：问句改写提示词（与 PromptTemplateBuiltinCatalog 同集）
-- 已建库须手工执行；新库请同步 schema_v1.sql 平台种子段

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'rag_query_rewrite_system', 'SYSTEM', 'RAG', 'zh-CN', '你是知识库检索问句专家。结合对话近史与当前用户消息，输出一行用于向量/关键词检索的查询文本（不是回答用户）。

规则：
1. 只输出一行检索问句，≤ 80 个汉字；禁止解释、markdown、引号、编号、换行。
2. 保留主题词、专有名词、实体与时间意图；多主题用空格分隔，尽量不用完整礼貌问句。
3. 删除：对 AI 的称呼、礼貌用语、「在知识库/查文档/检索」等动作词。
4. 追问、指代须结合近史补全检索意图，禁止脱离上文改写成无关主题。', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'rag_query_rewrite_user', 'USER', 'RAG', 'zh-CN', '当前日期：${today}（${year} 年）

【对话近史】
${conversation_history}

【当前用户消息】
${user_message}

输出一行知识库检索问句（仅输出问句本身）：', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO gw_api_endpoint (path_pattern, http_method, display_name, remark, enabled, sort_order, created_at, updated_at)
VALUES
('/api/v1/admin/rag-ltr/train', 'POST', '触发 RAG LTR 训练', '入队 RAG_LTR_TRAIN', 1, 2080, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('/api/v1/admin/rag-ltr/status', 'GET', 'RAG LTR 模型状态', NULL, 1, 2081, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
