# MySQL 脚本说明

> **应用进程配置**（端口、Redis、Milvus、RAG、JWT 等）见 **`src/main/resources/application.yml`** 内注释；本文档仅描述 **MySQL 脚本**。

## 目录结构

| 路径 | 用途 |
|------|------|
| **`schema_v1.sql`** | 全量 **DDL** + **幂等种子**（含 **`prompt_template` 平台默认**）+ 限流清单 |
| **`gw_api_endpoint_catalog_inserts.sql`** | 网关 **`gw_api_endpoint`** 全量 `INSERT IGNORE` 与后续追加 |
| **`migrate/`** | **已建库增量脚本**（`migrate_0_1_{patch}_*.sql`，`{patch}` = `pom` 补丁位） |

**提示词平台种子维护**：改 **`PromptTemplateBuiltinCatalog`** 后，用 **`PromptTemplateSeedGenerator`** 或 **`scripts/generate-prompt-seed-sql.py`** 生成 `INSERT IGNORE`，同步进 **`schema_v1.sql`**（新库）与对应 **`migrate/`**（已建库）。

## 唯一基线

- **`schema_v1.sql`**：全量 **DDL**（每列含 `COMMENT`、每表含 `COMMENT`）+ **幂等种子**（`INSERT IGNORE` 等，含 **`prompt_template` `tenant_id=0` 平台默认**）+ **接口限流清单**（仅当 `gw_api_rate_limit_rule` 为空时插入，避免重复执行整文件产生重复行）。
- **`gw_api_endpoint_catalog_inserts.sql`**：网关 **`gw_api_endpoint`** 接口目录的**全量 `INSERT IGNORE` 清单**与**后续追加档**（与 **`.cursorrules` §4.1.3** 约定一致）；新增对外 REST 时在本文件末尾追加一行即可。
- **新环境**：在空库上执行一次 `mysql ... < schema_v1.sql`，再执行 **`gw_api_endpoint_catalog_inserts.sql`**（含 **`chat_conversation_share`**、**`ten_scheduled_task`**、**`rag_web_crawl_site`**、**`prompt_template` 平台种子** 等当前全量表与网关目录）。
- **已存在数据的库**：请勿整文件重复执行 `schema_v1.sql`；按 **`migrate/`** 下对应补丁脚本手工增量执行，或仅摘取所需 `ALTER`/`INSERT` 片段。

## 运维附录（`migrate/`）

> 路径前缀均为 **`db/mysql/migrate/`**（例：**`migrate/migrate_0_1_257_prompt_template.sql`**）。

- 将 `admin` 提升为某租户 **FOUNDER**、历史表 **`sys_llm_model` 重命名** 等一次性语句，已以注释形式写在 **`schema_v1.sql` 文件末尾附录**，按需手工执行。
- **`migrate_0_1_80_sec_user_account_login_name_assign_id.sql`**：`sec_user_account` 登录列改名 **`login_name`**、主键去自增（与 **0.1.80** 代码一致）；已建库按需执行，与 **`pom.xml`/`PROJECT.md`** 补丁位对齐。
- **`migrate_0_1_212_profile_memory_device.sql`**：设备与用户绑定表、分层记忆抽象层/具体层表（与 **0.1.212** 代码一致）；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_90_rag_knowledge_workspace.sql`**：知识库工作台（分片策略、模型绑定、文档/分片逻辑删除等列，与 **0.1.90** 代码一致）；空库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_119_gw_api_endpoint.sql`**：网关 **`gw_api_endpoint`** 接口目录表及种子、相关 **`gw_api_rate_limit_rule`** 清单行（与 **0.1.119** 代码一致）；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_230_chat_conversation_share.sql`**：对话分享快照表 **`chat_conversation_share`**（**`share_code`** 全局唯一、**`snapshot_json`**、**`expires_at`**，与 **0.1.230** 后端及 **0.1.231** 用户端分享弹窗一致）；新库若 **`schema_v1.sql`** 已含该表可跳过。
- **`migrate_0_1_231_rag_web_crawl_schedule.sql`**：表 **`rag_web_crawl_url_item`**、一次性 **`web-crawl/local`** API；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_232_rag_retrieval_test.sql`**：知识库向量检索试跑 API；新库以 **`gw_api_endpoint_catalog_inserts.sql`** / 全量种子为准可跳过。
- **`migrate_0_1_233_ten_scheduled_task.sql`**：表 **`ten_scheduled_task`**（初版含爬站字段；**0.1.236** 迁出至 **`rag_web_crawl_site`**）；**`/admin/scheduled-tasks`** API；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_234_scheduled_tasks_menu.sql`**：管理端菜单 **`SCHEDULED_TASKS`**；新库以 **`schema_v1.sql`** 菜单种子为准可跳过。
- **`migrate_0_1_235_drop_legacy_web_crawl_schedule_api.sql`**：删除已废弃的 **`web-crawl/schedules`** 网关目录行；已建库按需执行。
- **`migrate_0_1_236_scheduled_generic_rag_web_crawl_site.sql`**：表 **`rag_web_crawl_site`**、**`ten_scheduled_task`** 通用 cron 列、爬站调度 **`RAG_WEB_CRAWL_DISPATCH`**；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_236_repair_after_1364.sql`**：**0.1.236** 部分失败后的修复（补表/补 API）；仅故障恢复时执行。
- **`migrate_0_1_237_rag_semantic_default_and_crawl_extract.sql`**：新建库默认语义分片、**`extract_config`**、**`preview-chunks`** API；新库以 **`schema_v1.sql`** + 目录清单为准可跳过。
- **`migrate_0_1_238_rag_parent_child_chunk.sql`**：**`rag_chunk.parent_chunk_id`** 及索引（子母分片母子关联；与 **`RagChunkStrategy.PARENT_CHILD`** 一致）；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_239_rag_ingest_analyze_api.sql`**：**`POST …/ingest/analyze`**、**`analyze-upload`** 网关目录行；新库以 **`gw_api_endpoint_catalog_inserts.sql`** 为准可跳过。
- **`migrate/migrate_0_1_257_prompt_template.sql`**：表 **`prompt_template`**、平台默认 LLM 提示词种子（与 **`PromptTemplateBuiltinCatalog`** 一致）、管理端菜单 **`PROMPT_TEMPLATES`** 与 **`/api/v1/admin/prompt-templates`** 网关目录；**已建库须手工执行**；新库 **`schema_v1.sql`** 已含表与平台种子，可只执行菜单/gw 段。
- **`migrate_0_1_240_chat_starter_prompt.sql`**：表 **`chat_starter_prompt`**、**`chat_starter_daily_batch`**、**`chat_starter_follow_up_cache`**、**`chat_starter_event`** 及推荐问题 Open/Admin API 目录行（与 **`pom.xml` `0.1.240-SNAPSHOT`** / **`PROJECT.md` `### 0.1.240-SNAPSHOT`** 一致）；**已建库（如 test_ai）必须执行**，否则推荐问题 API 报 `Table … doesn't exist`；新库若 **`schema_v1.sql`** 已含表可只补网关 **`INSERT IGNORE`** 段。
- **`migrate_0_1_241_chat_starter_scheduled_task.sql`**：为各租户种子 **`CHAT_STARTER_DAILY_HOT`** 定时任务（从旧 **`CHAT_STARTER_DAILY_HOT_*`** 运行时参数迁移 Cron/启停）；**0.1.241** 起热点由 **`ten_scheduled_task`** 调度，管理端在 **「定时任务」** 配置；**已建库**若未手工建该执行器任务则**须执行**。
- **`migrate_0_1_242_scheduled_run.sql`**：表 **`ten_scheduled_run`**（定时任务异步执行 run + **`progress_json`**）；网关补 **`scheduled-tasks/*/run/active`**、**`runs/*`**、**`job-tasks/*`** GET；与 **`pom.xml` `0.1.242-SNAPSHOT`** 一致；**已建库须执行**。
- **`migrate_0_1_243_web_search_grounding_cache.sql`**：种子 **`WEB_SEARCH_GROUNDING_CACHE_JSON`**（联网 Redis 缓存默认：语义近邻 + 6h/24h/48h + 会话复用）；**已建库须执行**；语义嵌入依赖 **`MEMORY_EMBEDDING_VECTOR_MODEL_ID`**（未配则退化为 hash 向量，近邻效果较弱）。
- **`migrate_0_1_245_crawl_framework.sql`**：表 **`crawl_run`**、**`crawl_url_queue`**（站点爬取框架持久化队列）；与 **`pom.xml` `0.1.245-SNAPSHOT`** 一致；**已建库须手工执行**；新库以 **`schema_v1.sql`** 为准可跳过。Playwright 部署：安装 Chromium（见 **`PROJECT.md` `### 0.1.245-SNAPSHOT`**）。
- **`migrate_0_1_247_chat_user_daily_recommend.sql`**：表 **`chat_user_daily_recommend`**、Open API **`/open/v1/chat/daily-recommend`**（与 **`pom.xml` `0.1.247-SNAPSHOT`** 一致）；**已建库须手工执行**；新库若 **`schema_v1.sql`** 已含表可只补网关 **`INSERT IGNORE`** 段。
- **`migrate_0_1_251_gw_access_party.sql`**：表 **`gw_access_party`**、**`gw_api_module`**、**`lnk_gw_module_endpoint`**、**`gw_access_party_grant`**、**`gw_access_party_call_log`**；扩展 **`gw_api_endpoint`**（**`module_id`**、**`global_rpm_cap`**、**`interface_kind`**）；Partner/Admin 接入方 API 目录行（与 **`pom.xml` `0.1.251-SNAPSHOT`** 一致）；**已建库须手工执行**；新库以 **`schema_v1.sql`** + **`gw_api_endpoint_catalog_inserts.sql`** 为准可跳过。
- **`migrate_0_1_252_sec_user_account_profile.sql`**：表 **`sec_user_account`** 增加 **`account_no`**、**`email`**（**`VARCHAR(191)`**，兼容 utf8mb4 唯一索引 767 字节上限）、**`phone`**、**`registration_channel`**、**`registered_at`**（与 **`pom.xml` `0.1.252-SNAPSHOT`** 一致）；**已建库须手工执行**；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_252_sec_user_account_email_index_fix.sql`**：若主脚本前半段已跑、**`uk_sec_user_email`** 报 **Error 1071** 时执行一次（收紧 **`email`** 后补建唯一索引）。
- **`migrate_0_1_253_gw_api_endpoint_spec.sql`**：表 **`gw_api_endpoint`** 增加 **`request_spec_json`**、**`response_spec_json`**（对接文档入参/出参；与 **`pom.xml` `0.1.253-SNAPSHOT`** 一致）；**已建库须手工执行**；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_254_knowledge_planet.sql`**：表 **`ten_user_knowledge_node`**、**`ten_user_weekly_insight`**、定时任务 **`KNOWLEDGE_PLANET_WEEKLY_*`**、Open API **`/open/v1/chat/knowledge-planet/*`**（与 **`pom.xml` `0.1.254-SNAPSHOT`** 一致）；**已建库须手工执行**；新库以 **`schema_v1.sql`** + **`gw_api_endpoint_catalog_inserts.sql`** 为准可跳过。
- **`migrate_0_1_255_web_search_knowledge.sql`**：**`chat_starter_prompt`** 增 **`query_normalized`**、**`query_norm_hash`**、**`grounding_json`**、**`hit_count`** 及 **`idx_csp_web_knowledge_lookup`**（**无**问句 UNIQUE，避免 utf8mb4 768 限制与阻断资讯多版本）；**已建库须手工执行**；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_255_web_search_knowledge_index_fix.sql`**：补 **`query_norm_hash`** + 索引（曾执行含 **`uk_csp_tenant_web_norm`** 旧脚本失败或缺列时按需执行）。
- **`migrate_0_1_256_message_center.sql`**：表 **`msg_channel`**、**`msg_template`**、**`msg_delivery_log`**；从 **`AUTH_REGISTER_VERIFICATION_JSON`** / **`KNOWLEDGE_PLANET_EMAIL_JSON`** 迁移 SMTP 与模板（**`SUBSTRING_INDEX` + `LIKE`**，**不依赖** `JSON_OBJECT`/`JSON_EXTRACT`/`JSON_UNQUOTE`，兼容 MySQL 5.6）；与 **`pom.xml` `0.1.256-SNAPSHOT`** 一致；**已建库须手工执行**；新库以 **`schema_v1.sql`** + **`gw_api_endpoint_catalog_inserts.sql`** 为准可跳过。
- **`migrate_0_1_256_message_center_menu.sql`**：管理端菜单 **`MESSAGE_CENTER`**（**消息发送**，挂在「租户配置」下）；**已建库**若侧栏无入口则**须执行**；新库以 **`schema_v1.sql`** 菜单种子为准可跳过。
- **`migrate_0_1_258_chat_conversation_public_id.sql`**：**`chat_conversation.public_id`**（开放 API 会话标识，非自增）；**已建库须手工执行**；新库以 **`schema_v1.sql`** 为准可跳过。
