# Ai 仓库 — Agent 协作说明

**权威流程**：**`.cursorrules` §0**（交付门禁全文）。本文件为速查，避免与 §0 漂移时以 §0 为准。

## 编码任务：§0.3 清单（同集必做）

1. **版本**：每次实质交付 → `PROJECT.md` **新开**顶节 `###`（文档补丁 +1，如 **0.1.259**）；**`pom.xml` 可选 bump**（不 bump 时 migrate 仍用 **`migrate_0_1_{pom补丁}_*`**，顶节内注明构件版本）。
2. **`PROJECT.md`「变更记录」**：顶节 `###` 补丁 **≥** `pom.xml`；至少一条 `- **模块**：…`；有库表则 **已建库须手工执行 migrate**。
3. **DB**：`schema_v1.sql`（含平台种子）+ `db/mysql/migrate/migrate_0_1_{patch}_*.sql`（`{patch}` = pom 补丁位）+ `gw_api_endpoint_catalog_inserts.sql` + `db/mysql/README.md`。
4. **大改可选**：`PROJECT.md` 功能模块索引 `##` + `.cursorrules` §1.5 一行。
5. **完成前**：`.\scripts\check-project-changelog.ps1 -IncludeUntracked` → 退出码 **0**。

**触发路径**（见 §0.2）：`modules/ai-*/src/main/java`、`modules/ai-*/src/test/java`、`web/user-web`、`web/admin-web`、`db/mysql`、`pom.xml`、`modules/ai-bootstrap/src/main/resources/application*.yml`。

## 当前开发线

- **变更记录顶节**：**`### 0.1.304-SNAPSHOT`**（见 **`PROJECT.md`**）
- **构件（Maven）**：**`pom.xml` `0.1.258-SNAPSHOT`**（migrate **`{patch}`** 仍以 pom 为准）

## 源码编码（UTF-8）— 全项目硬约束

**只允许 UTF-8。** 严禁用 GBK / GB2312 / GB18030 / Windows-1252 打开或保存源码。

| 动作 | 命令 / 文件 |
|------|-------------|
| 仓库声明 | **`.editorconfig`**、**`.gitattributes`** |
| IDE 一键配置 | **`.\scripts\setup-ide-encoding.ps1`**（**`config/idea/encodings.xml`**、**`config/vscode/settings.json`**） |
| 提交前扫描 | **`python tools/check_text_encoding.py`** 或 **`.\scripts\check-encoding.ps1`**（已接入变更记录门禁） |
| 从 Git 恢复 | **`python tools/restore_utf8_from_git.py <path>`**（勿用 **`Out-File`**） |
| 去 BOM | **`python tools/strip_java_bom.py`** |
| 权威细则 | **`.cursorrules` §0.6** |

IDEA：**Settings → Editor → File Encodings** → Global/Project **UTF-8**；**Transparent native-to-ascii for properties** 建议 UTF-8。Windows 控制台：**`application.yml`** **`logging.charset.console: UTF-8`**。

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
