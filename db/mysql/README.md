# MySQL 脚本说明

> **应用进程配置**（端口、Redis、Milvus、RAG、JWT 等）见 **`src/main/resources/application.yml`** 内注释；本文档仅描述 **MySQL 脚本**。

## 唯一基线

- **`schema_v1.sql`**：全量 **DDL**（每列含 `COMMENT`、每表含 `COMMENT`）+ **幂等种子**（`INSERT IGNORE` 等）+ **接口限流清单**（仅当 `gw_api_rate_limit_rule` 为空时插入，避免重复执行整文件产生重复行）。
- **`gw_api_endpoint_catalog_inserts.sql`**：网关 **`gw_api_endpoint`** 接口目录的**全量 `INSERT IGNORE` 清单**与**后续追加档**（与 **`.cursorrules` §4.1.3** 约定一致）；新增对外 REST 时在本文件末尾追加一行即可。
- **新环境**：在空库上执行一次 `mysql ... < schema_v1.sql`，再执行 **`gw_api_endpoint_catalog_inserts.sql`**（含 **`chat_conversation_share`**、**`ten_scheduled_task`**、**`rag_web_crawl_site`** 等当前全量表与网关目录）。
- **已存在数据的库**：请勿整文件重复执行；请用结构对比工具将库表对齐到本脚本，或仅摘取所需 `ALTER`/`INSERT` 片段。

## 运维附录

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
