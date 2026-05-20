# Ai 仓库 — Agent 协作说明

**权威流程**：**`.cursorrules` §0**（交付门禁全文）。本文件为速查，避免与 §0 漂移时以 §0 为准。

## 编码任务：§0.3 清单（同集必做）

1. **版本**：大改 → `pom.xml` 补丁 +1 + `PROJECT.md` **新开**顶节 `###`；小改 → 仅当前顶节追加条目。
2. **`PROJECT.md`「变更记录」**：顶节 `###` = `pom.xml` `<version>`；至少一条 `- **模块**：…`（含表名、REST、关键类）；有库表则写明 **已建库须手工执行 migrate**。
3. **DB**：`schema_v1.sql` + `migrate_0_1_{patch}_*.sql`（`{patch}` = pom 补丁位）+ `gw_api_endpoint_catalog_inserts.sql` + `db/mysql/README.md`。
4. **大改可选**：`PROJECT.md` 功能模块索引 `##` + `.cursorrules` §1.5 一行。
5. **完成前**：`.\scripts\check-project-changelog.ps1 -IncludeUntracked` → 退出码 **0**。

**触发路径**（见 §0.2）：`src/main/java`、`src/test/java`、`web/user-web`、`web/admin-web`、`db/mysql`、`pom.xml`、`application*.yml`。

## 当前开发线

以 **`pom.xml`** 为准（**`0.1.242-SNAPSHOT`**）。详见 **`PROJECT.md` 顶节 `### 0.1.242-SNAPSHOT`**。

## 相关文件

| 文件 | 用途 |
|------|------|
| `.cursorrules` §0 | 交付门禁、禁止项、文档分工 |
| `PROJECT.md` | 变更记录、版本策略、功能索引 |
| `.cursor/rules/project-changelog.mdc` | Cursor 勾选表（alwaysApply） |
| `db/mysql/README.md` | 迁移脚本清单 |
