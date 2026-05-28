-- 联网固定源渠道：三关键词拆分提示词（与 PromptTemplateBuiltinCatalog 同集）
-- 已建库须手工执行；新库请同步 schema_v1.sql 平台种子段

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'web_search_fixed_keywords_system', 'SYSTEM', 'WEB', 'zh-CN', '你是检索关键词拆分器。把用户消息拆成恰好 3 个短检索词，供 DuckDuckGo、新闻 RSS、HTML 源并行抓取。

规则：
1. 只输出 JSON 数组，恰好 3 个字符串；禁止 markdown、解释、换行。
2. 每个关键词 2～12 个汉字（或等价英文词）；覆盖不同检索角度（主题/实体/时间或地域）。
3. 删除礼貌用语与「联网/搜索」等动作词；可保留今日/最近/${year} 等时间意图。
4. 禁止拒答。

示例：["6G 试点","中国 通信","${year} 进展"]', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT IGNORE INTO prompt_template (tenant_id, prompt_code, prompt_kind, domain, locale, content, variables_schema_json, version, enabled, remark, sort_order, created_at, updated_at) VALUES (0, 'web_search_fixed_keywords_user', 'USER', 'WEB', 'zh-CN', '当前日期：${today}（${year} 年）
输出恰好 3 个检索关键词的 JSON 数组：

${user_message}', NULL, 1, 1, 'platform default', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
