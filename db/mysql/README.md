# MySQL 脚本说明

> **应用进程配置**（端口、Redis、Milvus、RAG、JWT 等）见 **`src/main/resources/application.yml`** 内注释；本文档仅描述 **MySQL 脚本**。

## 唯一基线

- **`schema_v1.sql`**：全量 **DDL**（每列含 `COMMENT`、每表含 `COMMENT`）+ **幂等种子**（`INSERT IGNORE` 等）+ **接口限流清单**（仅当 `gw_api_rate_limit_rule` 为空时插入，避免重复执行整文件产生重复行）。
- **`gw_api_endpoint_catalog_inserts.sql`**：网关 **`gw_api_endpoint`** 接口目录的**全量 `INSERT IGNORE` 清单**与**后续追加档**（与 **`.cursorrules` §4.1.3** 约定一致）；新增对外 REST 时在本文件末尾追加一行即可。
- **新环境**：在空库上执行一次 `mysql ... < schema_v1.sql` 即可。
- **已存在数据的库**：请勿整文件重复执行；请用结构对比工具将库表对齐到本脚本，或仅摘取所需 `ALTER`/`INSERT` 片段。

## 运维附录

- 将 `admin` 提升为某租户 **FOUNDER**、历史表 **`sys_llm_model` 重命名** 等一次性语句，已以注释形式写在 **`schema_v1.sql` 文件末尾附录**，按需手工执行。
- **`migrate_0_1_80_sec_user_account_login_name_assign_id.sql`**：`sec_user_account` 登录列改名 **`login_name`**、主键去自增（与 **0.1.80** 代码一致）；已建库按需执行，与 **`pom.xml`/`PROJECT.md`** 补丁位对齐。
- **`migrate_0_1_212_profile_memory_device.sql`**：设备与用户绑定表、分层记忆抽象层/具体层表（与 **0.1.212** 代码一致）；新库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_90_rag_knowledge_workspace.sql`**：知识库工作台（分片策略、模型绑定、文档/分片逻辑删除等列，与 **0.1.90** 代码一致）；空库以 **`schema_v1.sql`** 为准可跳过。
- **`migrate_0_1_119_gw_api_endpoint.sql`**：网关 **`gw_api_endpoint`** 接口目录表及种子、相关 **`gw_api_rate_limit_rule`** 清单行（与 **0.1.119** 代码一致）；新库以 **`schema_v1.sql`** 为准可跳过。
