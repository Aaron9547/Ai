# Ai 仓库 — Agent 协作说明

**权威流程**：**`.cursorrules` §0**（交付门禁全文）。本文件为速查，避免与 §0 漂移时以 §0 为准。

## 编码任务：§0.3 清单（同集必做）

1. **版本**：大改 → `pom.xml` 补丁 +1 + `PROJECT.md` **新开**顶节 `###`；小改 → 仅当前顶节追加条目。
2. **`PROJECT.md`「变更记录」**：顶节 `###` = `pom.xml` `<version>`；至少一条 `- **模块**：…`（含表名、REST、关键类）；有库表则写明 **已建库须手工执行 migrate**。
3. **DB**：`schema_v1.sql` + `migrate_0_1_{patch}_*.sql`（`{patch}` = pom 补丁位）+ `gw_api_endpoint_catalog_inserts.sql` + `db/mysql/README.md`。
4. **大改可选**：`PROJECT.md` 功能模块索引 `##` + `.cursorrules` §1.5 一行。
5. **完成前**：`.\scripts\check-project-changelog.ps1 -IncludeUntracked` → 退出码 **0**。

**触发路径**（见 §0.2）：`modules/ai-*/src/main/java`、`modules/ai-*/src/test/java`、`web/user-web`、`web/admin-web`、`db/mysql`、`pom.xml`、`modules/ai-bootstrap/src/main/resources/application*.yml`。

## 当前开发线

以 **`pom.xml`** 为准（**`0.1.253-SNAPSHOT`**）。详见 **`PROJECT.md` 顶节 `### 0.1.253-SNAPSHOT`**。

## 相关文件

| 文件 | 用途 |
|------|------|
| `.cursorrules` §0 | 交付门禁、禁止项、文档分工 |
| `.cursorrules` §4.1.3 | 枚举子包与扩展 checklist |
| `PROJECT.md` | 变更记录、版本策略、功能索引 |
| `.cursor/rules/project-changelog.mdc` | Cursor 勾选表（alwaysApply） |
| `db/mysql/README.md` | 迁移脚本清单 |

## 枚举词汇表（`common.api.enums.*`）

- **位置**：`com.aaron.cloud.common.api.enums.{域}`（`chat` / `rag` / `llm` / `tenant` / `job` / `scheduled` / `gateway` / `profile` / `guardrail` / `identity` / `metering` / `mcp` / `eval` / `intent` / `notify` / `file` / `infra`）。
- **判定**：会进库表、API 协议或扩展注册码 → 放 common 子包；仅某 Handler 内部状态机 → 可留业务包（如 `TravelReimbursementRound`）。
- **扩展 checklist**：① 正确子包新建枚举 ② 注册表类同步（`TenantRuntimeSettingKey` / `TenantScheduledExecutorCode` / `JobTaskType` / `ChatIntentHandlerKind` 等）③ 实体字段改用枚举 ④ 禁止业务字面量 ⑤ 无 DDL 变更则不必 migrate。
- **MyBatis**：`application.yml` → `mybatis-plus.type-enums-package: com.aaron.cloud.common.api.enums`（含子包）。
