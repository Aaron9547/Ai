-- 0.1.217：联网搜索模型类型（WEB_SEARCH）与 web_search_provider；对话前置检索实例配置于 llm_model。
ALTER TABLE llm_model
  ADD COLUMN web_search_provider VARCHAR(48) NULL
    COMMENT 'LlmWebSearchProvider：仅 model_kind=WEB_SEARCH 时使用，如 VOLCENGINE_ARK_BOT'
    AFTER vector_backend;
