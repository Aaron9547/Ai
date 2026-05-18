# Ai 项目说明

## 概述

- **坐标**：`com.aaron.cloud:Ai`（Maven）。
- **定位**：以 AI 对话为核心的 **AI 中台**（多模型、RAG/Milvus、智能体与工具、画像与长记忆、访客设备码、一键网页入库等）；前后端同仓，后端 Java（JDK 25），前端 Vue 3（`web/user-web` / `web/admin-web`）。
- **详细架构与编码约束**：见仓库根目录 **`.cursorrules`**（权威）；本文档侧重**对外说明**与**版本演进记录**。**`application.yml`** 侧重**进程启动、Bean 选路与基础设施连接**（键旁注释 + `${ENV:默认}`；EPP 桥接见 **`AiEnvironmentBridgePostProcessor`**，登记于 **`META-INF/spring.factories`**）。**按租户免重启的运行参数**（**`ten_runtime_setting`** / **`TenantRuntimeSettingKey`**，管理端「租户运行参数」API）与 yml 的分层约定见 **`.cursorrules` §3.8**。
- **管理端双语**：页面级文案以 **`web/admin-web`** 的 **vue-i18n** 为主；**部分接口返回的 label/placeholder** 由后端按 **`Accept-Language`** 拼装，见下文 **「管理端 Accept-Language 与 LLM 元数据（服务端文案）」**。
- **管理端租户成员与审计**：字段级契约见下文 **「管理端租户成员与审计写入规则（约定）」**；**重点逻辑注释及逻辑变更时须同步更新注释/专节** 见仓库根目录 **`.cursorrules` §7.2**。
- **本地中间件**：根目录 **`docker-compose.yml`** 提供 MySQL 与 **RocketMQ** 示例；端口与发现等见 **`application.yml`** 注释。

---

## 运行时配置

**进程启动、环境变量与中间件接入**仍以 **`src/main/resources/application.yml`** 及键旁注释为协作真源（与 **`AiEnvironmentBridgePostProcessor`** 的派生键关系见该类 Javadoc）。**按租户动态可调、不要求随应用重启才生效的参数**（如 **`WEB_SEARCH_GROUNDING_*`**、开放注册、出差报销 Coze、记忆嵌入模型 id 等）权威存储为 **`ten_runtime_setting`**（枚举 **`TenantRuntimeSettingKey`**）；**yml 与运行参数表的分层原则**见仓库根目录 **`.cursorrules` §3.8**。**Redis** 无 `ai.redis.enabled` 之类总闸：须配置 **`spring.data.redis.*`** 并成功建连，否则应用启动失败。本文档不重复展开全表。

---

## 管理端 Accept-Language 与 LLM 元数据（服务端文案）

与 **vue-i18n**（`viewMessages.*` 等）分工：**页面壳、按钮、校验提示** 仍走前端 i18n；**由接口下发的表单/表格元数据**（Tab 标题、列头、表单项 label 与 placeholder、枚举选项展示名）可走后端 **`MessageSource`**，以便与枚举、字段策略同仓演进。

| 环节 | 说明 |
|------|------|
| **接口** | **`GET /api/v1/admin/llm-models/meta`**：返回各 **`LlmModelKind`** 的 Tab、列表列、表单字段及 **`vectorBackends` / `webSearchProviders` / `connectorKinds`** 等 option 列表的展示文案；**VECTOR** 与 **WEB_SEARCH** 的列表/表单共用字段名 **`integrationBackend`**（库列 **`llm_model.integration_backend`**），下拉分别绑定 **`vectorBackends`** 与 **`webSearchProviders`**。 |
| **请求头** | 管理端 **`web/admin-web/src/plugins/http.ts`** 在拦截器中设置 **`Accept-Language`**，取值来自 **`localStorage`** 键 **`AI_ADMIN_LOCALE_LS_KEY`**（**`web/admin-web/src/stores/uiPreferences.ts`** 导出，与 Pinia 语言一致）；合法值为 **`zh-CN`**、**`en-US`**，否则回退 **`zh-CN`**。 |
| **Spring** | **`LlmModelAdminRestController#meta(Locale locale)`** 由 MVC 注入 **`Locale`**（默认 **`AcceptHeaderLocaleResolver`**，随 **`Accept-Language`** 解析）。 |
| **文案源** | **`application.yml`**：`spring.messages.basename: llm-admin-meta`；资源文件 **`src/main/resources/llm-admin-meta_zh_CN.properties`**、**`llm-admin-meta_en.properties`**（键前缀 **`llm.meta.*`**）。**`LlmModelAdminUiMetaService#buildMeta(Locale)`** 使用 **`MessageSource`**；缺 key 时回退 **`zh-CN`**，再缺则返回 key 避免 500。 |
| **前端刷新** | **`LlmModelManageView.vue`**：监听 **`useI18n().locale`**，切换语言后重新请求 meta；若当前 Tab 的 **`kind`** 在新响应中仍存在则保持选中。 |

扩展其它「接口驱动 UI 文案」时：复用同一 **`Accept-Language`** 约定，新增 basename 或共用 **`llm-admin-meta`** 并在对应 Service 中注入 **`MessageSource`** 即可。

---

## 联网搜索（功能模块索引）

**稳定边界（不写迭代清单）**

- **产品语义**：租户级 **对话前置联网检索**；用户侧可开关「联网」，须租户存在**已启用**的 **`LlmModelKind.WEB_SEARCH`** 实例（可用性见 **`GET /open/v1/chat/web-search-availability`**）。
- **配置入口**：管理端 **「大模型管理 → 联网搜索」** Tab，维护 **`llm_model`** 行（**`model_kind = WEB_SEARCH`**）；**`integration_backend`** 存 **`LlmWebSearchProvider`** 码，与 **VECTOR** 共用列名、分选项 **`webSearchProviders`**（见 **「管理端 Accept-Language 与 LLM 元数据」** 表内说明）。
- **编排位置**：**`ChatWebSearchGroundingService`** 由 **`ChatApplicationService#openAssistantSseStream`** 在 RAG 等之后、主 **`ModelInvokePort`** 之前注入网络检索 **system**；请求体 **`webSearchEnabled`**（及重试覆盖项）参与决策。
- **多轮检索与提示后缀**：轮数及各轮拼在用户问题后的说明为租户运行参数 **`WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT`**、**`WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON`**（**`TenantRuntimeSettingKey`**）；与 **`application.yml` 分层**见 **`.cursorrules` §3.8**。
- **引用持久化与 SSE**：检索归一化条目落 **`chat_message.meta_json#webSearchReferences`**（助手行写入；**同一轮 user 行**在助手落库后同步写入或移除该键，便于按轮次导出）；主流式前下发 **`webSearchRefs`** 分帧（**`v`** 为 **`{"references":[…]}`**）。**`GET …/conversations/{id}/messages`** 经 **`ChatMessageView`** 对 **user / assistant** 均解析 **`webSearchReferences`**。**`VolcArkBotWebSearchProvider`** 合并根 **`references`** 与 **`bot_usage…tool_details…results`**（按 URL 去重）。用户端 **`web/user-web`**（**`chat.ts` / `ChatView.vue`**）与管理端类型 **`chatAdmin.ts`** 对齐字段；迭代明细见 **「变更记录」** 当前顶 **`###`** 节。
- **扩展与实现真源**：**`WebSearchProviderRegistry`** / **`WebSearchModelProvider`**（首版 **`VolcArkBotWebSearchProvider`** 等）、**`SysLlmModel`** 解析、计量回写、**`WebSearchFlagDeserializer`** 等与周边模块的细则以代码及 **`LlmModelKind`** 注释为准。

**迭代写在哪里**：本专节**不随每次提交加长**。**文件、迁移、行为、前后端联调等变更**一律写在下方 **「变更记录」** 中**当前开发线对应的 `### x.y.z-SNAPSHOT` 节**（与 **`pom.xml` `<version>`** 对齐；**小改**并入该节，**大改** bump 后新开顶节，见 **「版本策略」**表）。**禁止**把迭代清单搬进本节以代替「变更记录」。

---

## ★ 用户端（`web/user-web`）路由与租户（**必读**）

> **C 端对话入口不是 `/chat`。** 正式路径为 **`/{租户编码}/chat`**（`租户编码` = 库表 **`sys_tenant.code`**，与种子 **`default`** 等一致），例如 **`/default/chat`**。地址栏**第一段**即当前工作区；出站请求优先带 **`X-Tenant-Code`**（该段为合法编码时）；若本地仍缓存旧版纯数字「租户段」则发 **`X-Tenant-Id`** 兼容（见 **`web/user-web/src/utils/outboundTenant.ts`** 与路由 **`beforeEach`**）。网关 **`TenantContextFilter`** 在 **`X-Tenant-Id` 缺省**时会将 **`X-Tenant-Code`** 解析为内部 **`tenantId`**（已登录 JWT 路径同样生效）。
>
> | 页面 | 路径 |
> |------|------|
> | 对话 | `/{租户编码}/chat` |
> | 对话分享（只读） | `/{租户编码}/share/{shareCode}` |
> | 设备与登录 | `/{租户编码}/system/me` |
>
> - **根路径 `/`**、旧书签 **`/chat`**、**`/system/me`**：重定向到 **`/{默认或本地缓存租户编码}/…`**（默认优先 **`VITE_TENANT_CODE`**，见 **`web/user-web/src/router/index.ts`**；**`VITE_TENANT_ID`** 仅作兼容兜底）。
> - **多租户切换**：直接修改 URL 为另一租户的 **`/租户编码/chat`**（或从运营下发的带租户前缀的入口进入）。已登录时须 JWT **`tms`** 中拥有该机位，否则接口 **403**。
> - **登录/注册成功**：前端会 **`router.push`** 到 JWT 主租户对应的 **`/{租户编码}/chat`**。
> - **`/…/system/me`（设备与登录）**：面向用户的「当前租户」说明以 **`sys_tenant.code`** 为准，**不**展示 **`sys_tenant.id`** 自增主键（与 **`.cursorrules` §7.1** 一致）。
> - **花生壳 / 内网穿透**：穿透域名（如 **`*.vicp.fun`**）访问 Vite dev 时，须在 **`vite.config.ts`** 配置 **`server.allowedHosts`**（仓库已对 **`.vicp.fun` / `.vicp.cc`** 放行）；若仍被拦截，可用环境变量 **`__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS`** 追加域名，或本地临时 **`allowedHosts: true`**（勿提交仓库）。

---

## 管理端租户成员与审计写入规则（约定）

本节为**实现与排障真源**：与 **`TenantMemberRoleApplicationService`**、**`AdminTenantMembersRestController`**、**`AdminAuthContextRestController`**（`jwt-local`）及 **`GlobalExceptionHandler`** 中对租户成员相关异常的映射一致；代码内对重点分支的注释均指向本节要点。

### 1. 权限与数据范围

| 能力 | 允许角色（JWT `memberRole`） | 说明 |
|------|------------------------------|------|
| 成员列表 `GET .../tenant-members` | 具备成员身份的调用方（非空 `memberRole`） | 默认仅返回 **`sys_tenant_member.status = ACTIVE`** 的行。 |
| 查询参数 **`includeInactive=true`** | **OWNER** 或 **FOUNDER** | 为 true 时不按成员状态过滤；**ADMIN** 等传入则 **403**。 |
| 创始人按 **`tenantId`** 查其它租户 | **FOUNDER** | 非创始人不得带 `tenantId`，且只能查当前 JWT 租户。 |
| **邀请**、**移出租户**、**改角色**（经本 Service 的写路径） | **OWNER** 或 **FOUNDER** | **ADMIN** 仅只读列表与自身业务菜单，不可改成员关系。 |
| **OWNER** 对行内角色的限制 | **OWNER** | 不能变更 **FOUNDER/OWNER** 既有行的角色或关系；不能把他人设为 **FOUNDER/OWNER**（与邀请、改角色、移除上的 `assertOwnerMutateRow` 一致）。 |
| **FOUNDER** 专属 | **FOUNDER** | 可分配 **FOUNDER**；可传 **`tenantId`/`tenantId` 覆盖** 操作任意已存在租户（须校验租户存在）。 |

### 2. 目标租户 `tid` 解析（与 JWT 一致）

- **未传** `tenantId`（或 body 中覆盖字段）：目标租户为 **`TenantContextHolder` 当前租户**；非创始人必须已有有效租户上下文。
- **创始人**且传入 **`tenantId` 覆盖**：目标租户以参数为准，用于跨租户运维。

### 3. 成员状态与业务错误

- **在册**：成员行 **`ACTIVE`** 方可 **改角色**、**移出租户**；否则抛出消息等于 **`ErrorCodes.EX_MSG_TENANT_MEMBER_INACTIVE`** 的 **`IllegalArgumentException`**，由 **`GlobalExceptionHandler`** 映射为 **`TENANT_MEMBER_INACTIVE`**（HTTP **400**），响应 **`message`** 为固定中文简述（勿把 `EX_MSG` 英文原文暴露给最终用户）。
- **重复邀请**：同一 `(tenantId, userId)` 已存在 **ACTIVE** 成员行时，抛出消息等于 **`ErrorCodes.EX_MSG_TENANT_MEMBER_ALREADY_ACTIVE`** 的 **`IllegalStateException`**，映射为 **`TENANT_MEMBER_ALREADY_ACTIVE`**（HTTP **409**）。
- **约定**：业务代码须使用 **`ErrorCodes.EX_MSG_*`** 常量作为 `getMessage()`，**禁止**手写同义字符串，否则全局映射失效。

### 4. `sys_audit_event` 落库字段（租户成员与上下文切换）

| 字段 | 约定 |
|------|------|
| **`tenant_id`** | **被操作租户**（成员关系所属租户；或切换后的工作租户）。 |
| **`actor_type`** | 固定 **`USER`**（管理端自然人）。 |
| **`actor_id`** | 操作者 **`sec_user_account.id`** 的字符串形式；若上下文无 `userId` 则空串。 |
| **`action`** | **`TENANT_MEMBER_INVITE`**：邀请或恢复成员；**`TENANT_MEMBER_REMOVE`**：移出租户；**`TENANT_MEMBER_ROLE_UPDATE`**：角色变更；**`ADMIN_CONTEXT_SWITCH`**：换发管理端 JWT 所选租户与角色。 |
| **`resource_type`** | 成员类事件为 **`sys_tenant_member`**；上下文切换为 **`admin_context`**。 |
| **`resource_id`** | 成员行主键字符串，或上下文切换时为 **`tenant_id` 字符串**（与 `resource_type` 语义一致即可）。 |
| **`detail_json`** | **JSON 对象**（UTF-8），键名稳定、可解析；见下表。 |

**`detail_json` 键约定（增字段可兼容，勿改已有键语义）**

| `action` | 建议键 | 含义 |
|----------|--------|------|
| `TENANT_MEMBER_INVITE` | `userId`, `loginName`, `role`, `reactivated` | 被操作用户 id、邀请入参登录名（与库一致）、目标角色、是否由已退出行恢复。 |
| `TENANT_MEMBER_REMOVE` | `userId`, `priorRole` | 被移出用户、移除前角色（**在 `setRoleCode` 未调用**前提下取自同一行）。 |
| `TENANT_MEMBER_ROLE_UPDATE` | `userId`, `from`, `to` | 被改用户、旧/新角色枚举名。 |
| `ADMIN_CONTEXT_SWITCH` | `role` | 切换后的管理端 **`TenantMemberRole`** 枚举名。 |

### 5. 审计失败策略

- 审计写入包在 **try/catch**：失败时 **`WARN`** 打日志（含 `action`、`tenantId`），**不回滚**主业务（避免「管不了审计就管不了租户」）。

---

## 版本策略

| 项 | 约定 |
|----|------|
| **权威来源** | `pom.xml` 中 `<version>`，与本文档「变更记录」**同步更新**。 |
| **开发线** | **0.1.x**：从 **0.1.0** 起；**补丁位**在「实质编码迭代」下按下行 **bump** 规则递增。 |
| **快照** | 开发阶段统一使用 **`-SNAPSHOT`** 后缀（例如 `0.1.0-SNAPSHOT`）。发布正式版时去掉 `-SNAPSHOT` 并按发布流程另开版本线（不在本文档展开）。 |
| **变更记录（必写）** | 凡合并入主线且改动了 **`src/main/java`**、**`src/test/java`**、**`web/user-web/`**、**`web/admin-web/`**、**`db/mysql/`** 迁移、**`pom.xml`**（含 **`<version>`** 与**生产依赖**）、**`application*.yml`** 等可运行产物，**必须**在「变更记录」留痕：**小改**在**当前** `### x.y.z-SNAPSHOT` 节内**追加或改写**条目（同一主题可合并为一条）；**大改**（独立能力、破坏性变更、需单独阅读的一整块主题）则 **bump `pom.xml` 补丁位**并**新增**一节 **`### x.y.(z+1)-SNAPSHOT`**，不宜继续挤在上一个补丁节里。 |
| **专节（`## …`）** | **一类：功能模块索引**——**新增可独立命名的产品能力**时增加短 **`##` 节**（例如 **`PROJECT.md`** 中的 **「联网搜索（功能模块索引）」**）：只写**稳定边界与入口**（产品语义、配置/编排位置、与周边关系、关键类型或表意），**不写迭代清单**。**二类：长期约定 / 协作说明**——如 **「★ 用户端路由与租户」**、**「管理端租户成员与审计写入规则」**、**「管理端 Accept-Language 与 LLM 元数据」**等，可作为字段级或流程真源保留表格与较长说明；**仍禁止**用任一类 **`##` 节**的扩写**代替**「变更记录」记录每次代码改动（动代码则变更记录必有条目）。 |
| **何时 bump `pom.xml`** | 与上表「大改」一致：出现**新一节变更记录**时，**须**同步递增 **`pom.xml`** 补丁位，使文档版本与构件版本一致。 |
| **何时不 bump** | **仅**修订 **`.cursorrules`**、**`PROJECT.md`**、其它**纯说明类 `.md`**（不涉及上表「变更记录必写」路径）时，**不递增**版本号；若有需要可在**当前** `###` 节下追加一句「文档修订」类说明。 |
| **修订说明（2026-05）** | 同质、同主题的**极小**文档或注释调整可合并叙述；**不**免除「动代码则变更记录必有条目」；**不**用专节顶替变更记录。 |
| **提交前自检** | 仓库根 **`.\scripts\check-project-changelog.ps1 -IncludeUntracked`**（校验：动代码须同集改 **`PROJECT.md`**，且顶节 **`###`** 与 **`pom.xml` `<version>`** 一致）。Agent 必读 **`AGENTS.md`**。 |

## 变更记录

### 0.1.231-SNAPSHOT

- **全量库表（`schema_v1.sql`）**：补登 **`chat_conversation_share`**（与 **`migrate_0_1_230_chat_conversation_share.sql`** 列定义一致，**`DATETIME(3)`** 与其它对话表对齐）；**新库**执行一次 **`schema_v1.sql`** 即含该表，**已建库**仍执行迁移档。
- **变更记录门禁（协作规则强化）**：**`.cursorrules` §0** 将交付门禁置于文首（**禁止**在 `.cursorrules` 使用 `.mdc` 的 `alwaysApply` frontmatter）；**§1** / **§9** 交叉引用 §0；**`AGENTS.md`**、**`.cursor/rules/project-changelog.mdc`** 要求完成前跑 **`check-project-changelog.ps1 -IncludeUntracked`** 且 **`PROJECT.md` 豁免**未点名 md 禁令；**`install-git-pre-commit-hook.ps1`** 可装 **`pre-commit`**。
- **对话分享（用户端体验与复制文案）**：**`ChatShareDialog`** — 轮次选择改为**单列列表**（间距与勾选态重设计，**`el-scrollbar`** 滚动）；长图预览区独立「长图预览」区块与卡片式预览舞台；底栏 **「复制分享」** 一键调用 **`POST …/shares`**（按选中轮次缓存链接）并复制**网盘式整段文案**（**`chatShareClipboard.ts`** + **`zh-CN` / `en-US`** **`shareClipboard*`** 键），不再单独「生成短链」输入框。**`ChatShareCaptureCard`** — 按**轮次块**渲染 user/assistant（块间距 **28px**、问答 **14px**），海报式渐变外框 + 白卡片；**`ShareCaptureTurn`** 结构替代扁平行列表。**`ChatShareView`** — 只读页与长图视觉对齐（**`messageBlocks`** 按轮分组）。依赖 **`html-to-image`**（**`chatShareImage.ts`**）不变。
- **用户端对话壳（同批）**：**`ChatView`** — 消息区 **`el-scrollbar`**；流式 Markdown **`renderStreamingMarkdownToSafeHtml`**；联网参考默认可折叠；流式结束 **`syncThreadAfterStream`** 减轻闪烁；用户气泡 Grid 避免复制钮压字；移动端 composer 间距/模型选择宽度微调（详见代码注释）。

### 0.1.230-SNAPSHOT

- **对话分享（快照短链，后端与初版前端）**：表 **`chat_conversation_share`**（迁移 **`db/mysql/migrate_0_1_230_chat_conversation_share.sql`**，全量见 **`schema_v1.sql`** 对话分享节）；**`snapshot_json`** 存标题与消息列表，**`share_code`** 全局唯一，**`expires_at`** 可空）；持久化 **`ChatConversationShare`** / **`ChatConversationShareRepository`**（**`common.chat`**）。**`ChatConversationShareService`**：创建时校验会话归属与 **`messageIds`**，生成短码；公开读取按租户上下文 + 过期校验。**`ChatShareController`**（**`rest.open`**）：**`POST /open/v1/chat/conversations/{id}/shares`** → **`sharePath`** 形如 **`/{tenantCode}/share/{code}`**；**`GET /open/v1/chat/shares/{code}`** 只读。**`ai.chat.share-expire-days`** 默认 **90**（**`@Value`**）。**`web/user-web`** 初版：**`ChatShareDialog`**、**`ChatShareCaptureCard`**、**`ChatShareView`**；路由 **`/{租户编码}/share/:shareCode`**（见 **「★ 用户端路由」**）；**`chat.ts`** **`createConversationShare` / `getPublicShare`**；**`chatShareTurns` / `chatShareImage`**。弹窗交互与复制文案见 **0.1.231**。
- **回复语种（`responseLocale`）**：**`ChatResponseLocalePrompt`** 归一化 **`zh-CN` / `en-US`** 并在 system 追加语种约束；**`ChatSendPayload` / `ChatRegenerateRequest`** 校验 **`responseLocale`**；**`ChatApplicationService`** 写入用户消息 **`meta_json.responseLocale`**，重试可覆盖。**`web/user-web`**：**`chatResponseLocale.ts`** 与 UI **`locale`** 同步出站。
- **用户端对话壳**：**`ChatSidebar`** 抽离侧栏；**`styles/chat-theme.css`** 与 **`global.css`** 主题变量；**`ChatView.vue`** 布局与消息操作条（分享接 **`ChatShareDialog`**）；**`plugins/elementPlusIcons.ts`**、**`utils/isAbortError.ts`**；**`MeView` / `LocaleThemeToolbar`** 与 **`zh-CN` / `en-US`** **`chat.*`、`me.*`** 文案补充。
- **多库 RAG 检索并行**：**`RagQueryBridgeService`** 对多 **`kbId`** 按嵌入模型分组，组内共享一次 embed 后以虚拟线程 **并行** Milvus/ES，再按入参 **`kbIds`** 顺序合并 snippets/citations（替代逐库串行）。
- **管理端租户壳**：**`TenantShellConfigView.vue`** 与 **`admin-web`** locales 文案/i18n 微调。
- **本地联调默认（`application.yml`）**：默认 **`ai.providers.file-storage=minio`**、**`MINIO_ENDPOINT`** 端口 **8991**（开发环境，非产品契约变更）。

### 0.1.229-SNAPSHOT

- **租户运行参数（对话 / 记忆 / 护栏）**：**`TenantRuntimeSettingKey`** 增加 **`CHAT_PROMPT_LIMITS_JSON`**、**`MEMORY_POLICY_JSON`**、**`CHAT_INPUT_GUARD_JSON`**（**`{}`** 表示代码默认）；**`ChatPromptLimitsRuntime`**、**`MemoryPolicyRuntime`**、**`ChatInputGuardRuntime`** 解析；**`TenantRuntimeSettingApplicationService`** 暴露 **`chatPromptLimits` / `memoryPolicy` / `chatInputGuardEffective`** 并在 **`validateAndNormalize`** 校验 JSON 对象。**`ChatApplicationService`**、**`ChatInputGuardService`**、**`UserMemoryApplicationService`**、**`MemoryAbstractAsyncPublisher`**、**`UserMemoryAbstractLlmWorker`**、**`MemoryAbstractRedisQueuePoller`** 按 **`tenantId`** 或消息内租户取策略；删除 **`AiChatPromptProperties`**、**`AiChatInputGuardProperties`**；**`AiMemoryProperties`** 仅保留 **`vector-enabled` / `milvus-collection` / abstract Redis 队列名与轮询间隔**。**`application.yml`** 去掉 **`ai.chat.prompt`**、**`ai.chat.input-guard`** 及已迁的 **`ai.memory.*` 策略键**。**`migrate_0_1_220_tenant_runtime_chat_memory_input_guard.sql`** 与 **`schema_v1.sql`** 种子 **`INSERT IGNORE`**。**`.cursorrules` §3.8** 与本文档对齐。
- **管理端系统参数页**：**`GET /api/v1/admin/tenant-runtime-settings`** 支持 **`current` / `size` / `keyword`** 分页与筛选（**`TenantRuntimeSettingApplicationService#pageEffectiveRows`**，与 MyBatis **`Page`** JSON 字段 **`records` / `total` / `size` / `current`** 对齐）；**`TenantRuntimeSettingsView.vue`** 检索 + **分页条**；**`tenantRuntimeSettings.ts`** 使用 **`fetchTenantRuntimeSettingsPage`**。
- **对话主链短期记忆（多轮 history）**：**`ChatPromptLimitsRuntime`** 扩展 **`historyMaxMessages` / `historyMaxCharsPerMessage` / `historyTotalMaxChars`**（仍由 **`CHAT_PROMPT_LIMITS_JSON`** 解析，缺省 40 / 12000 / 48000）；**`ChatApplicationService#openAssistantSseStream`** 在首条 system 之后按 **`lnk_chat_conversation_message`** 顺序注入当前 user 之前的 **user/assistant** **`MessageTurn`**（截断策略见上）；意图 SSE 外部长链不经此路径。**`TenantRuntimeSettingKey.CHAT_PROMPT_LIMITS_JSON`** 注释同步。
- **助手回合摘要与会话标题**：**`ChatTurnDigestApplicationService`** 在助手落库后异步写入 **`meta_json.contentSummary`**（主链、输入护栏、**`TravelReimbursementIntentRunner#finishPersist`** 均调度 digest）；构建历史时助手侧**优先**已生成的 **`contentSummary`**。**会话标题**：在 **`ChatApplicationService#streamUserMessage`**（及输入护栏拒绝路径）首条用户消息 **`linkMessage` 之后**同步用用户首行原文截断（**36** 字内）更新占位标题（「新会话」/「新对话*」），**不**再经 digest 二次 LLM 改标题；digest 内 **`needTitle`** 恒为 **false**。**`ChatMessageView`** / **`toChatMessageView`** / 前后端 **`contentSummary`** 与 **`ChatConversationControllerWebMvcTest`** 构造器与上文一致。
- **用户端 / 管理端 Markdown 围栏「复制代码」**：**`web/user-web/src/utils/renderMarkdown.ts`** 与 **`web/admin-web/src/utils/renderMarkdown.ts`** 覆写 **`fence`**：外包 **`md-code-block`** + 工具栏语言标签 + **「复制」**按钮；**`DOMPurify`** 增加 **`ADD_TAGS: ['button']`** 与 **`ADD_ATTR`**；**`document`** 点击委托写入剪贴板（**`clipboard` / `execCommand` 兜底**）。**`web/user-web/src/styles/global.css`**、**`web/admin-web/src/styles/global.css`** 增加 **`.md-code-*`** 样式（用户端含暗色）。
- **协作规则（`.cursorrules`）**：**§1** 增加条款：若 Cursor 全局「用户规则」中存在「未逐文件点名则禁止修改任意 **`*.md`**」类表述，**在本仓库不适用**；触及须留痕路径时**必须**维护本文「变更记录」；任务需要时可主动修订相关说明性 **`*.md`**，除非当次对话显式禁止某路径。
- **文档**：新增仓库根 **`README.md`**（项目简介、目录结构、主要依赖版本表、快速开始入口；详尽演进仍以本文「变更记录」与专节为准）。
- **协作与校验（变更记录门禁）**：**`AGENTS.md`**、**`.cursor/rules/project-changelog.mdc`**、**`scripts/check-project-changelog.ps1`**（**0.1.230** 起校验顶节 **`###`** 与 **`pom.xml`** 一致）；**`.cursorrules` §9–§10** 收敛为引用 **`AGENTS.md`**。

### 0.1.228-SNAPSHOT

- **EnvironmentPostProcessor 注册修正**：此前误用无扩展名的 **`META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor`**，Spring Boot **不会加载**，导致 **`ai.discovery.enabled`** 未桥接到 **`spring.cloud.discovery.enabled` / `eureka.client.*`**，表现为未显式开发现仍连 Eureka。改为官方约定的 **`META-INF/spring.factories`** 键 **`org.springframework.boot.env.EnvironmentPostProcessor`** 登记 **`AiEnvironmentBridgePostProcessor`**；删除错误文件；**`application.yml`** 头注释与 **`PROJECT.md`** 历史表述同步。

### 0.1.227-SNAPSHOT

- **文案与 Feign 默认**：去掉第三方工程名硬编码；**`ai.rag.local-embed-feign.service-id`** 占位默认 **`rag-embedding-svc`**（线上用 **`AI_RAG_LOCAL_EMBED_FEIGN_SERVICE_ID`** 覆盖为 Eureka 注册名）；**`application.yml`**、相关 Java 注释与 **`RagEmbeddingService`** 提示、**`llm-admin-meta_*.properties`**、本文件历史条同步。
- **配置（与现网联调环境逐项对齐）**：**`application.yml`** — **Eureka** `defaultZone` 与 rag **bootstrap** 字面一致（**`http://192.168.35.105:1200/eureka`**，无尾斜杠）；**`eureka.instance`** 增加与 rag 一致的 **lease** 默认；**`spring.data.redis`** 增加 **`database:0`**、**`timeout:5000ms`**（同 rag **`spring.redis.timeout`**）；**`ai.rag.local-embed-feign.base-url`** 默认与 **`aiengine.domain`** 一致（**含尾斜杠**）；RocketMQ / Milvus / ES / Sentinel / 索引等保持与 rag **application.yml** 同值；**MySQL** 仍为 **`test_ai`**（Ai 表），注释说明与 rag **`cloud`** 库同机同账号时可覆盖 **`MYSQL_URL`**。文件头增加「与 rag 对照」总说明。

### 0.1.225-SNAPSHOT

- **Redis（与对端环境对齐）**：对端 **`spring.redis.hostPort` 26379–26381** 为 **Sentinel**，非 Redis Cluster。**`AiEnvironmentBridgePostProcessor`** 新增 **`ai.redis.mode=sentinel`**，桥接 **`spring.data.redis.sentinel.master` / `sentinel.nodes[]`**；**`application.yml`** 默认 **`AI_REDIS_MODE=sentinel`**、**`AI_REDIS_SENTINEL_*`** 与 rag 同形；**`cluster`** 仍仅用于真 Cluster（数据端口）。**`AiEnvironmentBridgePostProcessorTest`** 覆盖 Sentinel 桥接与非法 mode。
- **RAG / 注册中心默认值**：**`ai.rag.elasticsearch.index-name`** 默认 **`rag_agent_documents`**（与 rag **`ElasticsearchProperties`** 一致）；**`ai.rag.local-embed-feign.base-url`** 默认 **`http://192.168.37.31/ly-ai-rag`**（与 rag **`aiengine.domain`** 去尾斜杠等价）；**`EUREKA_DEFAULT_ZONE`** 默认示例与 rag **bootstrap** 同网段（**192.168.35.105:1200**）。**`AiRagProperties` / `ElasticsearchRagSearchClient`** 空配置兜底索引名同步为 **`rag_agent_documents`**。

### 0.1.224-SNAPSHOT

- **联网检索功能模块（`WEB_SEARCH`）**：**`LlmModelKind.WEB_SEARCH`**；管理端「大模型管理 → 联网搜索」Tab；**`WebSearchProviderRegistry`** + **`WebSearchModelProvider`**，首版 **`VolcArkBotWebSearchProvider`**（**`RestClient`** 调火山 Ark Bot **Chat Completions** 非流式，配置来自 **`SysLlmModel`** 行，无硬编码密钥）。**`ChatWebSearchGroundingService`** 由 **`ChatApplicationService#openAssistantSseStream`** 在 RAG 等之后、主 **`ModelInvokePort`** 前注入网络检索 **system**；**`ChatSendPayload.webSearchEnabled`**、**`ChatRegenerateRequest`** 覆盖项；**`WebSearchFlagDeserializer`** 兼容布尔反序列化（若有）。
- **`llm_model.integration_backend`**：原 **`vector_backend`** 与 **`web_search_provider`** 合并为单列，**按 `model_kind`** 存 **`LlmVectorBackend`** 码或 **`LlmWebSearchProvider`** 码；**`migrate_0_1_217_llm_web_search.sql`**、**`migrate_0_1_218_llm_model_integration_backend.sql`**，**`schema_v1.sql`**；**`SysLlmModel#resolveVectorBackend` / `resolveWebSearchProvider`**；**`RagEmbeddingService`** 等改用 **`resolveVectorBackend`**。
- **可用性与网关**：**`GET /open/v1/chat/web-search-availability`**（**`WebSearchAvailabilityView`**）；**`gw_api_endpoint_catalog_inserts.sql`** 登记；**`SysLlmModelRepository`** 启用实例查询与默认实例选取。
- **计量**：**`ChatWebSearchGroundingService`** 解析 **`usage`** 与 **`bot_usage.model_usage[]`** 累加 token，**`LlmModelUsageRecorder`** 入账。
- **管理端 LLM**：**`LlmModelAdminUiMetaService` / DTO / ApplicationService`** 与 **`integrationBackend`**、**`llm-admin-meta_*.properties`**；**`web/admin-web`**：**`models.ts`**、**`LlmModelKindTabPanel.vue`**（列表 **启用** **`el-switch`** 直接 **`PUT`** 局部更新；编辑时 **API Key 留空不校验必填**；**`LlmModelFormFields`** 编辑态 API Key 不标必填）。
- **用户端**：**`chat.ts`**、**`ChatView.vue`**：**`getWebSearchAvailability`**、**`webSearchEnabled`** 与发送/重试联动。
- **文档**：「版本策略」与「联网搜索」专节按 **索引 + 变更记录** 分工维护：**「专节」表行**区分**功能模块短索引**与**长期约定 / 协作说明 `##` 节**；**「变更记录（必写）」**行补充 **`pom.xml` `<version>`**；「联网搜索」节条列稳定边界并泛化迭代落点（对齐「变更记录」当前 `###` 顶节与 **小改 / 大改**，不写死某一补丁号）。
- **联网引用（对话记录与回显）**：**`WebSearchReference` / `WebSearchReferenceView`** 扩展站点、logo、时效、`extraJson` 等；**`VolcArkBotWebSearchProvider`** 解析根 **`references`** 并兜底 **`bot_usage.action_details[].tool_details[].output.data.data.results[]`**，按 URL 去重合并。**`ChatApplicationService`**：主流式前 **`sendSseWebSearchRefFrames`**（SSE **`type: webSearchRefs`**）；**`buildAssistantMetaJson`** 写入 **`webSearchReferences`**；**`openAssistantSseStream`** 增加 **`pairedUserMessageId`**（首轮发送与重新生成均传入配对 **user** 消息 id），助手 **`insert`** 后 **`mergeWebSearchReferencesIntoUserMessageMeta`** 将引用写入或清空 **user** 行 **`meta_json`**；**`buildPriorVersionsChainBeforeReplace`** 快照保留 **`webSearchReferences`**。**`ChatMessageView` / `toChatMessageView`** 对 **user / assistant** 解析 **`webSearchReferences`**。**`web/user-web`**：**`chat.ts`**（**`WebSearchRefItem`、`StreamPart`、`parseSsePayload`**）、**`ChatView.vue`**（流式与历史、用户/助手气泡下「联网参考」条）。**`web/admin-web`**：**`chatAdmin.ts`**（**`WebSearchRefAdmin`**）。**`fillWebSearchReferencesArray` / `parseWebSearchReferencesFromRoot`** 复用落库与解析。**测试**：**`ChatConversationControllerWebMvcTest`** 中 **`ChatMessageView`** 构造参数对齐 **`webSearchReferences`** 字段。
- **配置**：**`application.yml`** 修正 Redis 默认值——**`spring.data.redis.port`** 与 **`ai.redis.cluster.nodes`** 不再误用 Sentinel 端口（26379–26381）作 Cluster 引导，改为数据端口示例（6379 起）；若环境为 **Sentinel+主从**，设 **`AI_REDIS_MODE=standalone`** 并指向可写主 **`host:6379`**，或覆盖 **`AI_REDIS_CLUSTER_NODES`**。
- **配置（注释）**：**`application.yml`** 的 **`ai.rag`** 段补充与 **对端 RAG 工程** 的对照说明（**`AI_RAG_ES_INDEX`** 与 RAG 侧 **`elasticsearch.index-name`**、**`AI_RAG_*_BASE_URL`** 与 **`aiengine.domain`**、**勿将对端环境下 `spring.redis`/`26379` hostPort 当作本工程 Cluster 节点**）。

### 0.1.223-SNAPSHOT

- **修订说明（规则）**：**`.cursorrules` §9 / §10** 已写明：触及「须写变更记录的路径」时 **`PROJECT.md`「变更记录」**为强制同步项；细则以 **§9 / §10** 为准。
- **文档**：本文档新增 **「管理端 Accept-Language 与 LLM 元数据（服务端文案）」** 专节，说明与 **vue-i18n** 的分工、**`Accept-Language`** 注入位置及 **`llm-admin-meta*.properties`** 维护方式。
- **管理端数据概览**：**`GET /api/v1/admin/dashboard/summary`**（**`AdminReadController`** → **`AdminDashboardApplicationService`**）按当前租户只读聚合 KPI、近 **24h**、近 **7** 日（北京日）HTTP/计量按日序列、**`last_login_region`** 分布、**`sys_http_access_log`** 客户端 IP TOP；**`/dashboard`** 页 **`DashboardView.vue`** + **`adminDashboard.ts`**，**ECharts** 折线/世界与中国地图（CDN GeoJSON，内网不可达时有告警）、柱状图，随 **`locale` / `isDark`** 重绘。**前端补充（同页迭代）**：KPI 数字用 **`@vueuse/core`** 的 **`useTransition`**（子组件 **`DashboardKpiBlock.vue`**，每次成功拉数后 **`kpiAnimKey`** 重挂载以从零缓动）；**`dash-data`** 在「已有 **`summary`** 时再次请求」上 **`v-loading`**（文案 **`views.dashboard.reloadingOverlay`**）；工作区切换成功后 **`window`** 派发 **`ai-admin-workspace-changed`**（**`src/constants/adminWorkspace.ts`**，**`AdminLayout.vue`** 成功切换后派发，**`DashboardView.vue`** 在 **`/dashboard`** 监听并 **`reload()`**），避免停留在概览页时数据仍属旧租户 JWT。
- **LLM 管理元数据多语言**：**`GET /api/v1/admin/llm-models/meta`** 按请求 **`Locale`**（来自 **`Accept-Language`**）从 **`MessageSource`** 拼装 Tab/列/表单/枚举展示名；**`LlmModelAdminUiMetaService`**、**`LlmModelAdminRestController`**、**`application.yml`** 与 **`llm-admin-meta_*.properties`**。**`web/admin-web`**：**`http.ts`** 统一带 **`Accept-Language`**（**`AI_ADMIN_LOCALE_LS_KEY`**）；**`LlmModelManageView.vue`** 监听 **`useUiPreferencesStore().locale`**（与顶栏 Pinia 一致）重拉 meta，**`el-tabs`** 加 **`key`**；**`LlmModelKindTabPanel.vue`** 接收 **`locale-tag`** 并为 **`el-table`** 加 **`key`**，避免英→中后 Tab/表头仍残留英文文案。
- **管理端意图弹窗**：**`IntentManageView.vue`** 再增大 **`el-dialog` 头/体/底** 与 **`intent-dlg-scroll`**、**`intent-dlg-form`** 留白；表单项纵向间距略增。**处理器 `el-select`**：触发器 **`el-select__wrapper`** 与下拉 **`popper-class`** 圆角与内边距优化（下拉挂载 body，独立非 scoped 样式）。

### 0.1.222-SNAPSHOT

- **管理端意图弹窗**：**`IntentManageView.vue`** 增大标题区、正文、底部与滚动区内边距；弹窗与表单区圆角、分隔线、轻阴影与渐变背景；表单项间距与输入圆角微调；**`el-dialog`** 宽度上限 **860px**。

### 0.1.221-SNAPSHOT

- **修订**：**`IntentHandlerParamSpec`** 将 **`name()`** 更名为 **`paramName()`**，避免实现类为枚举时与 **`Enum.name()`**（`final`）冲突。
- **意图处理器参数枚举真源**：新增 **`IntentHandlerParamSpec`** + **`TravelReimbursementHandlerParam`**（含 **`travelRouting`** 与 **`handlerParams`** 键），**`IntentHandlerParamSchemaBuilder`** 生成 **`IntentHandlerConfigFieldMeta`**；**`ChatIntentHandlerPlugin`** 以 **`handlerParamEnumClass()`** 声明枚举，**`configSchema()`** 默认推导。**`IntentHandlerConfigFieldMeta`** 增加 **`paramStorage`**（**`HANDLER_PARAMS` / `TRAVEL_ROUTING`**）、**`INT`** 类型及 **`intMin`/`intMax`**。**`ChatIntentHandlerKind`** 增加管理端 **`adminLabelZh` / `adminDescription`**；**`GET /api/v1/admin/chat/intent-handler-kinds`** 返回已注册类型；**`gw_api_endpoint_catalog_inserts.sql`** 追加目录行。**`web/admin-web`**：意图弹窗**移除扩展 JSON 与硬编码处理器列表**，处理器与表单项**完全由接口**驱动，保存时 **`mergeIntentExtraFromSchema`** 写回 **`extra_config_json`**。

### 0.1.220-SNAPSHOT

- **出差意图首轮顺序与 Coze 正文**：**`openStream`** 先处理<strong>首轮触发词</strong>重置为 DOC，再计算 **PLAN 直达**（修复同轮含续办默认词时误走第二工作流）；**`evaluateKeywordMatch`** 与之一致，触发词优先于续办匹配。**`TravelCozeResponseParser`** 过滤仅含 **`debug_url` / `node_execute_uuid`** 的 Coze 帧，避免当正文回显；**`TravelReimbursementIntentRunner`** 为各 **`workflowStage`** 补全步骤标题。**`web/user-web`**：**`ChatView.vue`** 意图分段改为与「思考」类似的顶栏 + 折叠、**Markdown** 渲染正文；新步骤 **loading** 时自动折叠上一步 **done**。

### 0.1.219-SNAPSHOT

- **意图处理器插件化与动态配置**：**`ChatIntentHandlerPlugin`** + **`IntentHandlerPluginRegistry`**；**`ChatIntentStreamRouter`** 按 **`chat_intent_definition.handler_kind`** 从注册表解析，不再硬编码出差分支。**`TravelReimbursementIntentRunner`** 实现插件接口，**`extra_config_json.handlerParams`** 与租户 **`ten_runtime_setting`**（**`TravelCozeRuntimeConfig`**）**非空合并**（意图侧优先）。**`GET /api/v1/admin/chat/intent-handlers/{kind}/config-schema`** 返回 **`IntentHandlerConfigFieldMeta`** 列表；**`gw_api_endpoint_catalog_inserts.sql`** 追加目录行。**`web/admin-web`**：**`IntentManageView.vue`** 按 schema 渲染 **`handlerParams`** 表单项（含 **`SECRET_STRING`** 遮罩），提交时 **`mergeIntentExtraConfig`** 写入 **`travelRouting` + `handlerParams`**。

### 0.1.218-SNAPSHOT

- **租户运行时参数敏感展示**：**`TenantRuntimeSettingKey`** 增加 **`maskSensitiveInAdminUi`**（当前 **`TRAVEL_REIMBURSE_*_COZE_API_KEY`** 为 **true**）；**`TenantRuntimeSettingApplicationService.TenantRuntimeSettingRow`** 增加 **`sensitive`**（API 仍返回明文 **`valueText`**，仅提示前端遮罩）。**`TenantRuntimeSettingsView.vue`**：有值且敏感时默认 **`••••••••`**，**「显示 / 隐藏」**切换表格内明文，**「修改」**或展开后点链接打开弹窗编辑。

### 0.1.217-SNAPSHOT

- **管理端系统参数页**：**`TenantRuntimeSettingsView.vue`** 字符串值去掉「点击编辑」文案，改为**主色 + 字重 + 下划线**的链接式可点样式，悬停略浅；完整值悬停 **`title`** 提示。

### 0.1.216-SNAPSHOT

- **`.cursorrules` §7.1**：增补**产品设计与工程实现分工**（用户可见文案与表/缓存/实现细节分离；工程说明落 **`PROJECT.md` / Javadoc / 折叠帮助**）。
- **管理端系统参数页**：**`TenantRuntimeSettingsView.vue`** 顶部说明改为**产品向**表述（工作区范围、保存后生效），不再在 Alert 中展开库表与 Redis 细节。

### 0.1.215-SNAPSHOT

- **管理端租户运行时参数页**：**`STRING`** 整行值区可点击（含「点击编辑」提示）打开弹窗；顶部说明写明 **MySQL `ten_runtime_setting` 落库**与 **Redis 仅缓存、写后双删**。
- **`TenantRuntimeSettingApplicationService`** 类注释标明权威存储为 **`ten_runtime_setting`** 及 Redis 双删语义，便于与「只改缓存」区分。

### 0.1.214-SNAPSHOT

- **管理端租户运行时参数页**：**`TenantRuntimeSettingsView.vue`**「重新加载」移至卡片标题栏右上角；去掉底部「保存」；**BOOLEAN** 开关切换即单键写库并刷新列表；**STRING** 表格内展示摘要与「修改」入口，弹窗编辑后「确定」即保存（`value_text` 与库 **`VARCHAR(1024)`** 对齐 **1024** 字上限）。

### 0.1.213-SNAPSHOT

- **出差报销两轮 Coze**：租户 **`ten_runtime_setting`** 增加 **`TenantRuntimeSettingKey`**：**`TRAVEL_REIMBURSE_COZE_DOMAIN`**、**`TRAVEL_REIMBURSE_DOC_COZE_API_KEY`**、**`TRAVEL_REIMBURSE_DOC_WORKFLOW_ID`**、**`TRAVEL_REIMBURSE_PLAN_COZE_API_KEY`**、**`TRAVEL_REIMBURSE_PLAN_WORKFLOW_ID`**（与 ly **`SystemConfigKey`** 下 **`TRAVEL_REIMBURSE_`** 前缀各键对齐）；**`STRING`** 类键允许空串写入。四轮工作流键均非空时，**`TravelReimbursementIntentRunner`** 在 **DOC** 调用文档工作流、**PLAN** 在模拟审批/冲突/知识库步骤后调用行程工作流（**`TravelCozeWorkflowClient`**）；否则仍为演示模拟。DOC 入参为 **`input`** + **`document_text`**（附件 **`extracted_text`** 拼接），无 ly 侧二进制 **`file`** 上传，Coze 工作流需兼容或后续接对象存储链路。**`TravelCozeRuntimeConfig`**、**`TenantRuntimeSettingApplicationService#travelCozeRuntimeConfig`** 收口读取。

### 0.1.212-SNAPSHOT

- **管理端意图关键词抽屉**：**`IntentManageView.vue`** 关键词抽屉由 **520px** 加宽至 **760px**，表格外包 **`overflow-x: auto`**、**`min-width`** 与操作列 **`fixed="right"`**，避免「命中」等列在窄抽屉内被裁切。
- **出差报销 PLAN 多轮续接**：**`TravelReimbursementIntentRunner.runPlanPhases`** 在「您还未发起出差申请单」模拟分支**不再** **`CACHE.remove`**，保留 **PLAN** 与 **`docSummary`**，后续「继续 / 下一步」及 **`PLAN_CONTINUE`** 配置词可再次命中；**`evaluateKeywordMatch`** 流程续接条件**去掉**对 **`planDirectConsumed`** 的绑定（该标志仅约束 **`openStream`** 内一次性 **PLAN 直达**），避免用过一次直达后续接词永远进不了意图。

### 0.1.211-SNAPSHOT

- **意图多轮与配置通用化**：**`TravelIntentRoutingConfig`** 从 **`chat_intent_definition.extra_config_json`** 解析 **`travelRouting`**（**`allowDocAdvanceWithAttachmentOnly`** 默认 **true** 保持历史行为；**false** 时 DOC 阶段须正文含首轮触发词才命中，「仅附件」走大模型）；可选 **`sessionExpiredUserHint`** 覆盖会话失效 SSE 提示。**`TravelReimbursementIntentRunner.evaluateKeywordMatch`** 接入该开关。**`web/admin-web`**：**`intentAdminMeta.ts`** 集中处理器/关键词类型展示名；**`IntentManageView.vue`** 顶部说明与处理器下拉去硬编码出差文案，关键词类型改为「首轮/单轮」「流程续接」；**材料阶段**开关与扩展 JSON 同步写入 **`travelRouting`**。

#### 意图 `extra_config_json.travelRouting`（`TRAVEL_REIMBURSEMENT`）

| 键 | 类型 | 默认 | 说明 |
|----|------|------|------|
| **`allowDocAdvanceWithAttachmentOnly`** | boolean | **true** | **true**：DOC 等待材料时，用户仅带附件也可命中意图（多轮兼容）。**false**：须同轮正文含「首轮」触发短语（可与附件同发）；仅附件不命中，走大模型主链。 |
| **`sessionExpiredUserHint`** | string | 内置中文 | 会话缓存缺失时 SSE **`content`** 提示文案（勿写密钥）。 |

### 0.1.210-SNAPSHOT

- **意图对话体验**：**`TravelReimbursementIntentRunner.evaluateKeywordMatch`** 将 **首轮触发词** 判定提前到 **DOC+附件** 之前，避免会话卡在 DOC 时第二轮「触发语 + 附件」永远只命中 `DOC_ATTACHMENT` 而无法按触发语重置状态；**`web/user-web` `ChatView.vue`** 在存在 **`workflowSegments`** 时不再渲染主 Markdown 气泡（分段卡片与落库 `content` 拼接正文重复的问题）。

### 0.1.209-SNAPSHOT

- **意图关键词命中可追溯与统计**：用户/助手消息 **`meta_json`** 写入 **`intentHitKeywordId`**、**`intentHitPhrase`**、**`intentHitKeywordKind`**、**`intentMatchSource`** 等（与 **`ChatIntentMatchSource`** 对齐）；**`GET /open/v1/chat/conversations/{id}/messages`** 响应 **`ChatMessageView`** 增加 **`intentTurnHit`**。**`chat_intent_keyword.hit_count`** 在带库关键词 id 的意图 SSE 完成后 **`+1`**（内置续办/DOC 无 id 不计）；存量库可执行 **`db/mysql/migrate_0_1_209_chat_intent_keyword_hit_count.sql`**。**管理端** 关键词列表 **`KeywordRow.hitCount`** 与 **`IntentManageView`**「命中次数」列；**用户端** 历史加载后在用户气泡下展示简短命中说明（短语 + 来源中文）。

### 0.1.208-SNAPSHOT

- **意图链路可观测日志**：**`ChatIntentStreamRouter`**、**`ChatApplicationService`**（用户消息落库后意图路由前后）、**`TravelReimbursementIntentRunner`**（openStream 入参摘要、PLAN 直达、DOC 缺附件、会话状态缺失）增加 **`[意图链路]`** 前缀 **INFO** 日志，与 ly **`DialogueApiService`** 排障风格对齐；**`messagePreview`** 取用户正文前 **80** 字。

### 0.1.207-SNAPSHOT

- **管理端意图识别租户范围**：列表与 CRUD **仅 JWT 当前工作区租户**；**`AdminQueryTenantSupport.resolveIntentAdminDataTenantId`** 收口（创始人亦不得借 **`filterTenantId` / `targetTenantId`** 跨租户）。**`IntentRow`** 不再返回 **`tenantId`**；**`ChatIntentDefinitionRepository.listForAdmin`** 已移除（避免创始人全量跨租户列表）。**`web/admin-web`** 意图页去掉租户列、筛选与「目标租户」表单项；**`chatIntent.ts`** 与接口路径不再传租户查询参数。

### 0.1.206-SNAPSHOT

- **MySQL 767 字节索引与意图关键词表**：**`chat_intent_keyword.phrase`** 由 **`VARCHAR(255)`** 改为 **`VARCHAR(128)`**，保证 **`UNIQUE KEY uk_chat_intent_kw_intent_phrase (intent_id, phrase)`** 在 **`utf8mb4`** + 旧 **`innodb_large_prefix`/行格式** 下不超过 **767** 字节，避免 **`SQL 错误 [1071]`**。**`schema_v1.sql`** 与 **`migrate_0_1_205_chat_intent.sql`** 已同步；若曾用旧 DDL 仅 **`CREATE TABLE chat_intent_keyword`** 失败，可重跑更新后的 **`migrate_0_1_205`**（**`IF NOT EXISTS`** 跳过已建表）。若库中已是 **`VARCHAR(255)`** 且需保留数据，执行 **`db/mysql/migrate_0_1_206_chat_intent_keyword_phrase_len.sql`**（短语须均 **≤128** 字符）。**`ChatIntentAdminDtos`** 关键词 **`phrase`** 增加 **`@Size(max = 128)`**。

### 0.1.205-SNAPSHOT

- **对话意图识别（可扩展 + 管理端配置）**：库表 **`chat_intent_definition`**、**`chat_intent_keyword`**；管理端菜单 **`CHAT_INTENTS`**（**`/chat/intents`**）、**`/api/v1/admin/chat/intents`** CRUD 与关键词维护；**`ChatIntentStreamRouter`** 在用户消息后优先匹配启用意图，命中则由 **`TravelReimbursementIntentRunner`**（当前为与 ly 对齐的**演示用分段状态机**，**`extra_config_json`** 预留接真实编排）经 SSE 推送 **`workflowStage`** 帧；助手消息 **`meta_json`** 含 **`workflowSegments`** 供历史恢复。**用户端** **`ChatView.vue`** 工作流卡片样式（分阶段 **loading / streaming / done**）；**重新生成** 流同样合并 **`workflowStage`**。存量环境执行 **`db/mysql/migrate_0_1_205_chat_intent.sql`**；网关目录见 **`gw_api_endpoint_catalog_inserts.sql`**。

### 0.1.204-SNAPSHOT

- **管理端 `/users` 刷新按钮样式**：**`UsersView.vue`** 两处刷新由 **`text`** 改为 **`type="primary" plain`**，与 **审计 / 访问日志 / MCP / 会话** 等列表页刷新一致（描边按钮）；仍保留 **`users-refresh-btn`** 固定宽度，避免 **`:loading`** 时头栏抖动。

### 0.1.203-SNAPSHOT

- **管理端 `/users` 刷新按钮**：**`UsersView.vue`** 为「租户成员」「本租户账号」两处刷新按钮增加 **`users-refresh-btn`**（**`min-width` + `flex-shrink: 0`**），避免 **`el-button` `:loading`** 插入图标后变宽，在卡片 **`space-between`** 头栏下带动左侧筛选项左右抖动。

### 0.1.202-SNAPSHOT

- **对话向量阈值仅知识库**：删除 **`ai.rag.chat-vector-min-cosine-score`**、**`AiRagProperties#chatVectorMinCosineScore`**；**`RagQueryBridgeService`** 仅按 **`rag_knowledge_base.chat_vector_min_cosine_score`** 过滤，列/实体缺失时代码兜底 **0.65**。**多库**：**`searchSnippetsAcrossKnowledgeBases` / `searchCitationHitsAcrossKnowledgeBases`** 已按每个 **`kbId`** 分别检索并在各自 **`hitsTo*`** 内解析该库阈值，不会混用一条全局配置。

### 0.1.201-SNAPSHOT

- **知识库级对话向量阈值**：表 **`rag_knowledge_base.chat_vector_min_cosine_score`**（默认 **0.65**，**0**=关闭该库过滤）；**`RagKbAdminDtos` / `RagKbAdminApplicationService`** 与 **`web/admin-web` 高级设置** 可读写；新建库 **`RagApplicationService.createKb`** 显式 **0.65**。存量库执行 **`db/mysql/migrate_0_1_201_rag_kb_chat_vector_min_cosine.sql`**（新库整跑 **`schema_v1.sql`** 已含列则不必）。**0.1.202** 起不再使用全局 yml 兜底，仅以库列为准。

### 0.1.200-SNAPSHOT

- **对话 RAG 弱相关仍落库引用**：**`RagQueryBridgeService`** 对 Milvus 向量召回按 COSINE 分数过滤弱命中，同步作用于片段与 **`meta_json#ragCitations`**；**`ChatApplicationService.openAssistantSseStream`** 在片段与引用皆空时将本回合 **`intent`** 降为 **`CHAT_ONLY`**，避免短问候等仍写入/展示挂名知识引用。（初版曾提供全局 **`ai.rag.chat-vector-min-cosine-score`**，**0.1.202** 起已移除，阈值仅以知识库列为准。）

### 0.1.199-SNAPSHOT

- **用户端对话 RAG 条误挂**：**`web/user-web` `ChatView.vue`** 消息列表 `:key` 由 **`会话+序号`** 改为 **`messageRowKey`**（优先库表 **`id`**，乐观行用 **`clientRowKey`**）；避免 Vue 复用上一轮助手气泡 DOM，导致新一轮（如天气）仍显示上一轮（如奖学金）的「参考文档」条。SSE 累积 **`ragRetrievalTitles`** 改为每次 **新数组替换**，避免与旧引用纠缠。

### 0.1.198-SNAPSHOT

- **RAG Elasticsearch 去掉 `uris`**：**Ai** 侧节点**仅** **`ai.rag.elasticsearch.config.host-ports`**（与对端 RAG 工程 **`elasticsearch.config.hostPorts`** 一致），删除 **`ai.rag.elasticsearch.uris`** / **`AI_RAG_ES_URIS`** 及 **`AiRagProperties.Elasticsearch#uris`**；**`ElasticsearchRagHostParser`**、**`ElasticsearchRagClientEnabledCondition`**、**`ElasticsearchRagSearchClient`**、**`ElasticsearchRagHostParserTest`**、**`application.yml`** 同步。历史节 **0.1.85 / 0.1.86** 中「`uris` 非空」表述改为 **`config.host-ports` 非空**。

### 0.1.197-SNAPSHOT

- **关 Eureka 时的本地嵌入**：**`ai.rag.local-embed-feign.base-url`** 支持 **`${AI_RAG_LOCAL_EMBED_FEIGN_BASE_URL:${AI_RAG_ENGINE_BASE_URL:}}`**（**`AI_RAG_ENGINE_BASE_URL`** 与对端 RAG 工程 **`aiengine.domain`** 同语义）；**`RagEmbeddingService`** 在未装配 Feign 时按 **`ai.discovery.enabled`** 分支给出明确 **`IllegalStateException`** 说明。**`application.yml`** 注释与 **`AiRagProperties`** / **`RagLocalEmbeddingFeignAutoConfiguration`** Javadoc 同步；顺带修正 **`ai.rag.elasticsearch`** 占位与 **`enabled`** 默认与 **0.1.196** 约定一致。

### 0.1.196-SNAPSHOT

- **RAG Elasticsearch 配置收敛**：**`ai.rag.elasticsearch`** 与对端 RAG 工程 **`elasticsearch`** 同形——**`enabled`** 总闸（默认 **false**）、**`config.cluster-name` / `host-ports` / `user-name` / `password`**、**`index-name`**；**`host-ports`** 默认空，启用混合检索时再填或设 **`AI_RAG_ES_HOST_PORTS`**；可选 **`uris`**。去掉顶层重复 **`host-ports`** 与 **`AI_RAG_ES_CONFIG_*`**。**`ElasticsearchRagHostParser`** / **`ElasticsearchRagClientEnabledCondition`** / **`AiRagProperties`** / **`ElasticsearchRagHostParserTest`** 同步。

### 0.1.195-SNAPSHOT

- **RAG Elasticsearch 与对端 RAG 工程 对齐**：**`ElasticsearchRagHostParser`** 统一解析 **`ai.rag.elasticsearch.uris`**（逗号/分号多节点）、**`host-ports`**、**`config.host-ports`**（与 RAG 服务 **`elasticsearch.config.hostPorts`** 同形，缺省 **`http://`**）；**`ElasticsearchRagSearchClient`** 使用 **`RestClient.builder(多 HttpHost)`**；**`AiRagProperties.Elasticsearch`** 增加 **`hostPorts`** 与 **`config`**（**`userName`/`password`** 作顶层 **`username`** 缺省时的账号来源）。**`ElasticsearchRagClientEnabledCondition`** 与 **`application.yml`** / **`ElasticsearchRagHostParserTest`** 同步。

### 0.1.194-SNAPSHOT

- **Actuator Elasticsearch 健康与 RAG ES 解耦**：**`AiEnvironmentBridgePostProcessor`** 将 **`management.health.elasticsearch.enabled`** 仅映射为 **`MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED`**（缺省 **false**），**不再**随 **`AI_RAG_ES_ENABLED=true`** 自动开启，避免 **`ElasticsearchRestClientHealthIndicator`** 在 ES 未监听时反复 **`Connection refused`** WARN；需在 **`/actuator/health`** 中探 ES 时显式 **`MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=true`**。**`application.yml`** 键旁注释与 **`AiEnvironmentBridgePostProcessorTest`**（**`elasticsearchHealthDefaultOff_evenWhenAiRagEsEnabled`**、**`elasticsearchHealthOnWhenMgmtEnvTrue`**）同步。

### 0.1.193-SNAPSHOT

- **Eureka 可选装配**：**`@EnableDiscoveryClient`** 从 **`AiApplication`** 迁至 **`EurekaDiscoveryOptionalConfiguration`**，仅 **`ai.discovery.enabled=true`** 时生效；**`AiEnvironmentBridgePostProcessor`** 在 **`ai.discovery.enabled=false`** 时额外写入 **`eureka.client.register-with-eureka=false`**、**`eureka.client.fetch-registry=false`**，与 **`eureka.client.enabled=false`** 一起禁止注册/拉表，避免关总闸后仍连 **`EUREKA_DEFAULT_ZONE`**。**`AiEnvironmentBridgePostProcessorTest#discoveryOff_disablesRegisterAndFetch`**。

### 0.1.192-SNAPSHOT

- **配置与运行时可观测（合并叙述，原 0.1.189～0.1.192 同类改动）**
  - **统一环境桥接**：**`AiEnvironmentBridgePostProcessor`** 在 **`META-INF/spring.factories`** 登记为 **`EnvironmentPostProcessor`**（**0.1.228** 前曾误用无扩展名路径，见该版修正）；**`ai.discovery.enabled`** → **`spring.cloud.discovery.enabled`** / **`eureka.client.enabled`**；**`ai.rocketmq.name-server`**、**`producer-group`** → **`rocketmq.*`**；**`ai.providers.oauth2-resource-server.jwt-issuer-uri`**（**`AI_OAUTH2_JWT_ISSUER_URI`**）→ **`spring.security.oauth2.resourceserver.jwt.issuer-uri`**；**`MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED`**（优先）否则 **`AI_RAG_ES_ENABLED`** → **`management.health.elasticsearch.enabled`**。**`AiEnvironmentBridgePostProcessorTest`** 覆盖桥接。（**注**：曾存在的 **`ai.redis.enabled=false` → `spring.autoconfigure.exclude`（Redis）** 已移除，Redis 为必选。）
  - **`application.yml`**：**`ai.discovery`** 为发现总闸唯一 YAML 占位；去掉与 EPP 重复的 **`rocketmq.*`**、**`spring.security.oauth2.*`**、**`management.health.elasticsearch.enabled`**；删除已无引用的 **`ai.rag.embedding.*`**（向量化以 **`llm_model`** VECTOR + **`RagEmbeddingService`** 为准）；去掉与默认相同的 **`ai.cors`** / **`ai.chat.input-guard`** 冗余键；**`AiRagProperties`** 去掉 **`Embedding`**、**`LocalEmbedFeign.serviceId`** 与 YAML 对齐；**`AiProvidersProperties`** / **`RocketMqAppProperties`** 与上述键对齐。随后在保持精简的前提下，为 **`auth` / `vector-store` / `remoting` / `rag` / Redis·MQ·发现** 等恢复**可选值与能力说明**类键旁注释（无历史版长分区横幅）。
  - **说明**：上列若属**同批次小步提交**，变更记录**并为本节**，不单开 **`0.1.189`～`0.1.191`** 多节；见上表「修订说明（2026-05）」。

### 0.1.188-SNAPSHOT

- **`application.yml` 维护分区重排**：文件头增加 **维护分区索引**（①～⑦）；**Redis**（`spring.data.redis` + `ai.redis` + 用量队列/TTL）、**服务发现**（`spring.cloud.discovery` + `eureka` + `ai.discovery`）、**RocketMQ**（`rocketmq` + `ai.rocketmq`）、**鉴权**（`spring.security.oauth2` issuer + `ai.providers.auth`）、**RAG/ES**（`ai.rag.elasticsearch` + **`management.health.elasticsearch`**）等 **官方键与中台键同主题就近**，仅调整顺序与注释，**不改键名与默认值**。

### 0.1.187-SNAPSHOT

- **Eureka 本地默认关闭**：**`AI_DISCOVERY_ENABLED` / `ai.discovery.enabled`** 默认值由 **true** 改为 **false**，**`spring.cloud.discovery.enabled`**、**`eureka.client.enabled`** 内层默认同步为 **false**，避免本机无法访问 **`EUREKA_DEFAULT_ZONE`**（如内网 **192.168.*.***）时出现首次拉表长时间阻塞与 DiscoveryClient 周期性 **Connection timed out** 刷屏；需要注册/发现时再显式 **`AI_DISCOVERY_ENABLED=true`**。**`RagLocalEmbeddingFeignCondition`** 缺省与 YAML 对齐。**`MilvusVectorStore`** 连接成功日志改为英文，减轻 Windows 控制台编码导致的乱码。

### 0.1.186-SNAPSHOT

- **Redis 单总闸（历史）**：**`AI_REDIS_ENABLED` / `ai.redis.enabled`** 曾为 **false** 时合并 **`spring.autoconfigure.exclude`** 排除 **`RedisAutoConfiguration`** 等；**该开关与排除逻辑已移除**，Redis 须可用，见上文 **「运行时配置」**。

### 0.1.185-SNAPSHOT

- **配置单入口收敛（续）**：**`ai.providers.infra-via-discovery`** 改为 **`${AI_INFRA_VIA_DISCOVERY:false}`** 单 env，**不再**内层引用 **`AI_DISCOVERY_ENABLED`**（Milvus/MinIO 经 Eureka 解析须显式 **`AI_INFRA_VIA_DISCOVERY=true`**，与 **`AI_DISCOVERY_ENABLED`** 注册总闸分离）。**`management.health.elasticsearch.enabled`** 默认 **`${AI_RAG_ES_ENABLED:false}`**，与 **`ai.rag.elasticsearch.enabled`** 同一 **`AI_RAG_ES_ENABLED`** 心智；仍可用 **`MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED`** 单独覆盖（例如已开 RAG ES 但不想 Actuator 探 ES）。**`AiProvidersProperties`**、**`.cursorrules` §4.3.1** 同步。

### 0.1.184-SNAPSHOT

- **服务发现单一心智模型**：新增 **`ai.discovery.enabled`**（**`${AI_DISCOVERY_ENABLED:true}`**），**`spring.cloud.discovery.enabled`** 与 **`eureka.client.enabled`** 均引用该键，运维只需记 **一个环境变量 `AI_DISCOVERY_ENABLED`**（或 YAML 覆盖 **`ai.discovery.enabled`**）。**`RagLocalEmbeddingFeignCondition`** 改为读取 **`ai.discovery.enabled`**；**`RagEmbeddingService`**、**`SysLlmModel`**、Feign 类 Javadoc 与 **`application.yml`** 注释同步。

### 0.1.183-SNAPSHOT

- **本地嵌入 Feign 与 Eureka**：**`RagLocalEmbeddingFeignClient`** 的 **`name`** 改为 **`ai.rag.local-embed-feign.service-id`**（默认占位 **`rag-embedding-svc`**，须与对端在 Eureka 上的 **`spring.application.name`** 一致）；**`url`** 为 **`ai.rag.local-embed-feign.base-url`**，**空则走 LoadBalancer + Eureka**，非空则直连。**`RagLocalEmbeddingFeignCondition`** 在 **`base-url` 非空** 或 **（发现已开且 `service-id` 非空）** 时装配（**0.1.184** 起「发现已开」以 **`ai.discovery.enabled`** 为准）；**`application.yml`** 增加 **`service-id`** 与注释。**`RagEmbeddingService`** 异常文案同步。

### 0.1.182-SNAPSHOT

- **管理端 RAG 知识库 API 路径**：由 **`/api/v1/admin/rag-kbs/{kbId}/...`** 调整为 **`/api/v1/admin/rag-kbs/{tenantCode}/{kbId}/...`**，首段 **`{tenantCode}`** 为 **`sys_tenant.code`**（与 JWT 工作区一致，服务端 **`RagKbAdminApplicationService.assertPathTenantCode`** 校验）；**`{kbId}`** 仍为 **`rag_knowledge_base.id`**。**`RagKbAdminRestController`**、**`ApiV1ControllerBases.RagKbAdmin`**；**`web/admin-web/src/api/ragAdmin.ts`** 从 **`memberships` + JWT tid`** 解析路径段（回退 **`VITE_TENANT_CODE`** / **`default`**）。**`gw_api_endpoint_catalog_inserts.sql`**、**`schema_v1.sql`** 限流种子、**`migrate_0_1_182_gw_api_endpoint_rag_kb_tenant_path.sql`** 同步。

### 0.1.181-SNAPSHOT

- **管理端知识库文档下载**：**`KbDocumentMatrixPanel`** 导出 Markdown 时，下载文件名去掉常见源后缀（如 `.doc`/`.docx`/`.pdf` 等）再补 **`.md`**，避免 **`xxx.doc.md`**；标题已为 **`.md`** 时不再重复追加。

### 0.1.180-SNAPSHOT

- **管理端 LLM 模型弹窗**：**`LlmModelAdminUiMetaService`** 缩短表单 **label**、列表列名与向量嵌入下拉项文案；长说明迁入 **placeholder**。**`LlmModelKindTabPanel`** 弹窗标题 **「新建/编辑 · {Tab 中文名}」**；表单 **`label-position="left"`**、**`label-width="112px"`**、**`size="small"`**，正文区 **`max-height` + 滚动** 控制总高度，宽度 **580px**。**`LlmModelFormFields`** 紧凑行距与 checkbox 左栏标签。**`.cursorrules` §7.3** 同步为「左标签 + 弹窗内滚动」约定（不推荐一律顶置标签）。

### 0.1.179-SNAPSHOT

- **llm_model.local_deploy**：库表与管理端 **`localDeploy`**（默认 **false**）；**VECTOR** 且为 **true** 时 **`RagEmbeddingService`** 经 **`RagLocalEmbeddingFeignClient`** 调用 **`POST …/{tenantCode}/privateModel/embedding`**，与对端嵌入网关 **`PrivateModelController`** 同类接口；路径变量 **`tenantCode`** 为 **`sys_tenant.code`**。根地址：**`ai.rag.local-embed-feign.base-url` 非空则直连**；否则 **`ai.discovery.enabled=true`**（**`AI_DISCOVERY_ENABLED`**，**0.1.184** 起与 **`spring.cloud.discovery.enabled` / `eureka.client.enabled`** 同源）时以 **`ai.rag.local-embed-feign.service-id`**（默认 **`rag-embedding-svc`**）经 Eureka + LoadBalancer 解析（**0.1.183** 起）。请求体 **`model` + `input`**；响应 **`data[0]`** 为向量。迁移 **`migrate_0_1_179_llm_model_local_deploy.sql`**；**`schema_v1.sql`**、**`application.yml`**、**`RagLocalEmbeddingFeignSupportTest`**；管理端 **`models.ts`** / **`LlmModelKindTabPanel`** 同步。

### 0.1.178-SNAPSHOT

- **画像注入与「轮次」误解**：**`TURN_COUNT`** 为 **`ten_profile_tag`** 按主体（`u:userId` / `d:deviceId`）**跨会话累加**，此前 **`buildPromptAddendum`** 写「累计对话轮次」易被模型理解为本会话轮数。**`UserProfileApplicationService`** 改为「历史累计发言…（跨会话统计，非本条会话内轮数）」；**`LAST_USER_EXCERPT`** 前缀改为「跨会话最近一条…」；类与 **`ProfileTagCode`** 注释标明语义边界。**`ChatApplicationService`** 画像块标题改为「跨会话统计，非本会话消息条数」并加行内注释。

### 0.1.177-SNAPSHOT

- **用户端对话 RAG 条串台**：**`ChatView.vue`** 增加 **`assistantStreamGeneration`**，在 **`send` / `retryAssistantAt`（重新生成）** 发起流式前自增；SSE **`onPart`** 若代数已过期则丢弃（避免上一轮 **`ragDoc`** 写入本轮助手气泡）。**`ragDoc`** 仅在 **`m.streaming`** 时追加。**`deepCloneReplyVariants`** 对 **`ragRetrievalTitles`** 做数组拷贝；**`mapHistoryToMsgs`** 对扁平与最后一版 variant 使用**独立**标题数组，无引用时不写入 **`ragRetrievalTitles`** 键。

### 0.1.176-SNAPSHOT

- **对话 RAG 检索查询串**：**`ChatApplicationService.buildRagLexicalSearchQuery`** 改为以 **`ChatSendPayload#getContent()`**（本轮用户输入）为向量/词法检索主文本，**不再**使用含附件长文的 **`augmentedUserText` 全文**，避免嵌入被文摘带偏；无正文仅附件时退回附件块之前的片段；检索串为空时本轮**不**走 RAG 注入。**`USER_ATTACHMENT_BLOCK_MARKER`** 与 **`buildUserMessageWithAttachments`** 共用字面量。
- **用户端参考文档展示**：**`ChatView.vue`** 从 **`listConversationMessages` 的 `ragCitations`** 恢复 **`ragRetrievalTitles`**（流式结束后的 **`loadMessagesForConv`** 不再丢失）；「参考文档」条移至**正文气泡下方**，样式上边距调整。

### 0.1.175-SNAPSHOT

- **对话流式 URL**：**`OpenAiChatStreamClient.resolveChatCompletionsUrl`** 不再在「非 `/v1` 结尾」的根地址后自动插入 **`/v1`**，仅统一追加 **`/chat/completions`**（已以 **`/v1`** 结尾则 **`…/v1/chat/completions`**；已含完整 **`…/chat/completions`** 则不变），避免火山方舟 **`…/api/v3`** 被误拼为 **`…/api/v3/v1/chat/completions`**。OpenAI 官方请在模型配置中将 Base 设为 **`https://api.openai.com/v1`**。**`OpenAiChatStreamClientUrlTest`** 与 **`LlmModelAdminUiMetaService`** 语言类 Base URL 占位说明同步。

### 0.1.174-SNAPSHOT

- **向量嵌入策略**：新增 **`LlmVectorBackend.VOLCENGINE_ARK_MULTIMODAL`**（库列 **`vector_backend`** 存 **`VOLCENGINE_ARK_MULTIMODAL`**），对应火山方舟 **多模态向量化** **`POST …/api/v3/embeddings/multimodal`**（[向量化文档](https://www.volcengine.com/docs/82379/1409291?lang=zh)）：请求体为 **`model` + `input` 内容块**（RAG 纯文本仅 **`{"type":"text","text":"…"}`**），成功响应解析 **`data.embedding`** 单数组；与 **`VOLCENGINE_ARK`**（**`/embeddings`** + OpenAI 形态 **`input` 字符串**）区分。**`VectorEmbeddingsUrl`**、**`RagEmbeddingHttpSupport`**（组装 JSON / 解析响应）、**`RagEmbeddingService`** 接入；**`VectorEmbeddingsUrlTest`**、**`RagEmbeddingHttpSupportTest`**。管理端 **`llm-models/meta`** 增加下拉项与 Base URL 占位说明；**`web/admin-web` `models.ts`** 类型同步。存量库可选执行 **`migrate_0_1_174_llm_model_vector_backend_multimodal.sql`** 更新列注释；**`schema_v1.sql`** 同步。

### 0.1.173-SNAPSHOT

- **管理端模型管理**：**`GET /api/v1/admin/llm-models/meta`** 下发 **`LlmModelAdminMetaResponse`**（Tab、列表列、表单字段、**`vectorBackends` / `connectorKinds`** 等选项），由 **`LlmModelAdminUiMetaService`** 从 **`LlmModelKind` / `LlmVectorBackend` / `LlmConnectorKind`** 与注册表组装；**`GET /api/v1/admin/llm-models?modelKind=`** 支持按类型筛选。**`LlmModelAdminView`** 非向量类型 **`vectorBackend`** 为 **null**。**前端**拆为 **`views/model/llm/LlmModelManageView.vue`**（Tab）+ **`LlmModelKindTabPanel.vue`** + **`LlmModelFormFields.vue`**，按元数据渲染；原 **`LlmModelsView.vue`** 删除。**`gw_api_endpoint_catalog_inserts.sql`** 与 **`schema_v1.sql`** 登记 **`/llm-models/meta`**。**`KbAdvancedSettingsDialog`** 并行拉取语言/向量模型列表。

### 0.1.172-SNAPSHOT

- **向量嵌入路径策略**：**`llm_model.vector_backend`**（**`LlmVectorBackend`**）落地实体与 **`LlmModelAdminApplicationService`**；**`OPENAI_COMPATIBLE`** 按内网统一嵌入网关常见形态拼接 **`{base}/v1/embeddings`**（Base 已以 **`/v1`** 结尾则只补 **`/embeddings`**），修复 **`…/rag` + `/embeddings`** 误拼导致的 **404**；**`VOLCENGINE_ARK`** 为兼容根仅追加 **`/embeddings`**（与官方向量化 API 路径一致）。**`VectorEmbeddingsUrl`** + **`VectorEmbeddingsUrlTest`**；**`RagEmbeddingService`** 中性 **400** 文案、解析 Spring **`error`+`path`** 错误体。存量库缺列执行 **`db/mysql/migrate_0_1_172_llm_model_vector_backend.sql`**（脚本内将已有 **`VECTOR`** 行 **`vector_backend`** 置为 **`VOLCENGINE_ARK`** 以保持原「仅追加 **`/embeddings`**」行为；内网统一网关（服务根下 **`/v1/embeddings`**）请在管理端改为 **`OPENAI_COMPATIBLE`**）；**`schema_v1.sql`** 列注释同步。**管理端**模型表单向量类型可选嵌入路径策略。

### 0.1.171-SNAPSHOT

- **管理端壳层（`web/admin-web`）**：侧栏 **`el-aside`** 改为顶栏品牌固定、**`.side-menu-scroll`** 内菜单单独纵向滚动，并定制细滚动条（`scrollbar-width` + WebKit）；**`el-menu`** 使用受控 **`openeds`**，**`@open`** 仅保留当前展开分组、**路由变更**时同步展开对应分组，实现同时只展开一个一级分组。

### 0.1.170-SNAPSHOT

- **管理端知识库高级设置**：「向量模型（嵌入）」表单项下移除冗长说明文案，仅保留选择器与「清空」。

### 0.1.169-SNAPSHOT

- **RAG 嵌入上游错误**：`OpenAI` 兼容接口返回 **4xx**（如火山方舟 **`InvalidEndpointOrModel.NotFound`**：Model ID / 端点不存在或 Key 无权限）时，**`RagEmbeddingService`** 解析响应体 **`error.code` / `error.message`**，抛出 **`ResponseStatusException(400)`**（**5xx** 为 **502**），避免误映射为全局 **500**；管理端可读到明确配置提示。

### 0.1.168-SNAPSHOT

- **向量化 Base URL**：**不再**在服务端统一默认插入 **`/v1`**；由后续 **`vector_backend` / `VectorEmbeddingsUrl`**（**0.1.172**）按策略拼接路径。本版前后端侧重「兼容根自配 + 仅补 **`/embeddings`**」说明。
- **`job_task.rag_kb_id`**：新增可空列与 **`idx_job_task_tenant_rag_kb`**（**`schema_v1.sql`** + **`migrate_0_1_168_job_task_rag_kb_id.sql`**）；**`RagApplicationService`** 入队 RAG 任务时写入。**`JobTaskRepository#pageByTenant`** 在 **`ragKbId`** 筛选时优先 **`rag_kb_id = ?`**；列为空的历史行用 **`payload_json LIKE`**（**`CONCAT(CHAR(37)…)`** 拼出 **`%"kbId":<id>`** 后跟 `,` / `}` / `]` 三种形态）回退，**不再调用 `JSON_EXTRACT`**（避免部分库报 **`FUNCTION … JSON_EXTRACT does not exist`**）。

### 0.1.167-SNAPSHOT

- **向量化 URL（火山方舟 / 豆包）**：方舟兼容根常以 **`/api/v3`** 结尾，嵌入路径为 **`…/api/v3/embeddings`**。官方文档：[向量化 API（火山方舟）](https://www.volcengine.com/docs/82379/1523520?redirect=1&lang=zh)。（**0.1.168** 起路径拼接规则见上条，不再单独对 **`/v3`** 做启发式分支。）

### 0.1.166-SNAPSHOT

- **RAG 嵌入**：`openai_base_url` 与嵌入路径拼接规则曾迭代（避免误拼根路径导致对端 404）；**0.1.168** 起须自配兼容根，仅追加 **`/embeddings`**。无 API Key 时不发送 **`Authorization`**。**`VECTOR`** 模型创建时 Key 可选；知识库绑定向量模型不再强制 Key；管理端 **`clearApiKey`** 可清除已存密钥。

### 0.1.165-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**异步任务**从独立路由改为**当前选中知识库**标题栏内的弹窗（**`KbAsyncTasksDialog`**）；**高级设置**从 **`/knowledge-center/workspace/:kbId`** 改为弹窗（**`KbAdvancedSettingsDialog`**）。旧地址 **`/knowledge-center/tasks`** 重定向至 **`/knowledge-center/knowledge-bases`**。**`KbDocumentMatrixPanel`** 拉取任务列表时传入 **`ragKbId`**，与弹窗筛选一致。
- **后端**：**`GET /api/v1/admin/job-tasks`** 增加可选查询参数 **`ragKbId`**；按知识库筛选语义见 **0.1.168**（**`rag_kb_id` 列 + LIKE 回退**，不依赖 **`JSON_EXTRACT`**）。

### 0.1.164-SNAPSHOT

- **UTF-8 BOM 批量清理**：仓库 **`src/**` 下仍有大量带 BOM 的 `.java`（IDE 常只显示前 ~100 条 `非法字符: '\ufeff'`）。已新增脚本 **`tools/strip_java_bom.py`**，对本线执行一次后从 **17** 个 Java 源文件首字节移除 **EF BB BF**（幂等，可反复执行）。后续若合并/粘贴再次带入 BOM，可在项目根执行：`python tools/strip_java_bom.py`。

### 0.1.163-SNAPSHOT

- **`ChatConversationControllerWebMvcTest`**：`package` 误为 `ai.chat.rest.open`，与目录 **`com.aaron.cloud.chat.rest.open`** 不一致导致编译失败；已改为正确包名（与同包 **`ChatConversationController`** 联编无需额外 import）。
- **MyBatis-Plus 3.5.16**：**`SecUserAccountRepository`** / **`SysTenantRepository`** 将弃用的 **`selectBatchIds`** 改为 **`selectByIds`**。
- **`ElasticsearchRagSearchClient`**：ES 查询文档类型由原始 **`Map`** 改为 **`Map<String, Object>`**（`MAP_DOC_CLASS`），消除 raw type / unchecked 告警。

### 0.1.162-SNAPSHOT

- **UTF-8 BOM（`\uFEFF`）**：对 **`ChatRegenerateRequest`**、**`FeignTenantContextInterceptor`**、**`RagHtmlToMarkdown`**、**`MilvusVectorStore`**、**`DocumentTextExtractor`**、**`ChatSensitiveTermAdminDtos`**、**`RagKbAdminRestController`** 等以无 BOM 方式重写保存，消除 `非法字符: '\ufeff'` 及紧随其后的「需要类、接口…」级联解析错误；顺带将明显乱码的 Javadoc/日志句改为可读中文（**`MilvusVectorStore`** 与 **`VectorStorePort`** 同包，不引入错误 `import`）。

### 0.1.161-SNAPSHOT

- **编码损坏残留**：修复 **`ChatAttachmentUploadService`**（空文件/会话不存在/类型提示、**未闭合**的占位文案字符串）、**`GuardrailSensitiveTermAdminService`**（多处 **`ResponseStatusException` 消息缺 `"`**、**`splitImportLines` 正则字面量损坏**）、**`ElasticsearchRagSearchClient`** 构造提示、**`ChatApplicationService`** 中 `parseChatSendFromStoredUser` 与 **`openAssistantSseStream` Javadoc** 乱码/断行问题，消除「一文件语法错、多文件红线」的级联。

### 0.1.160-SNAPSHOT

- **`RagVectorInfrastructure`**：`assertMilvusOrThrow` 中 **503 提示字符串缺少闭合 `"`**，导致编译失败并在 IDE 中对依赖该类型的多个文件产生级联红线；已补全并整理类注释为可读中文。

### 0.1.159-SNAPSHOT

- **`LlmModelOption`**：去除文件首 **UTF-8 BOM（`\uFEFF`）** 导致的 `非法字符: '\ufeff'`；注释改为可读中文。

### 0.1.158-SNAPSHOT

- **`ChatApplicationService`**：修复因编码/合并换行导致的**未闭合字符串字面量**与**整行被 `//` 误注释**（`chatRagKbIds` 声明、SSE 失败分支 `hint` 等），恢复可编译；用户可见文案与系统提示中的乱码改为规范简体中文；去除重复的 **`java.util.Map`** import。

### 0.1.157-SNAPSHOT

- **RAG 嵌入（BGE-M3 / 兼容端）**：Milvus 路径下向量化统一为 **OpenAI 兼容 `POST {openaiBaseUrl}/embeddings`**，由知识库 **`assigned_embedding_model_id`** 绑定租户 **`llm_model`**（**`model_kind=VECTOR`**）；与常见 BGE-M3 网关及后续豆包、千问等「兼容嵌入」形态一致。**`RagEmbeddingPort#embed(tenantId, kbId, text)`**；**`RagKbVectorModelGuard`** 在爬取/文件入队、入库、分片写向量、对话检索开启等处强制校验。
- **库表**：**`rag_knowledge_base.assigned_embedding_model_id`**（迁移 **`db/mysql/migrate_0_1_157_rag_kb_assigned_embedding_model.sql`**）；**`schema_v1.sql`** 同步。
- **管理端**：知识库「高级设置」分列「对话绑定（语言）」与「向量模型（嵌入）」；**`LlmModelKind.VECTOR`** 文案调整为嵌入/RAG；知识库列表开启对话检索前校验已绑定向量模型。
- **新建知识库**：**`chat_retrieval_enabled`** 默认 **OFF**（先绑定向量模型再手动开启对话检索，与「无 Milvus 不参与检索」产品语义一致）。

### 0.1.156-SNAPSHOT

- **RAG × Elasticsearch**：**`ai.rag.elasticsearch.config.user-name` / `password`**（**`AI_RAG_ES_USERNAME`**、**`AI_RAG_ES_PASSWORD`**）；可选 **`ai.rag.elasticsearch.username`** 非空时覆盖 **config**（与对端 RAG 工程 **`elasticsearch.username` / `config.userName`** 一致）。与 **`management.health.elasticsearch`** 分流见类 Javadoc。

### 0.1.155-SNAPSHOT

- **对话模型**：移除 **`ai.providers.model` / `ai.providers.openai.*`** 全局 OpenAI 兼容对话回退；非 **`mock`** 时必须命中租户 **`llm_model`**（否则 **`IllegalArgumentException`**，REST 映射为 **400** 与可读说明）。租户端点仍走 **`OpenAiChatStreamClient`**（管理端配置的 Base URL + 密文 API Key）。

### 0.1.154-SNAPSHOT

- **自定义配置前缀**：业务自有键统一为 **`ai.*`**（与 Spring 官方 **`spring.*` / `eureka.*` / `rocketmq.*`** 分离）；**`ai.rocketmq.*`** 承载 MQ 开关、Job 分发与用量 topic/消费组（**`RocketMqAppProperties`**），**`rocketmq:`** 下仅保留 starter 文档中的 **`name-server`**、**`producer.*`**。
- **Java 包名修复**：此前误将部分 **`package com.aaron.cloud.chat|rag|remoting...`** 替换为 **`package ai.*`** 的文件已恢复，组件扫描与编译与 **§4.1** 包结构一致。

### 0.1.153-SNAPSHOT

- **RocketMQ 配置去重**：移除 **`com.aaron.cloud.rocketmq.*`**；与 **`rocketmq-spring-boot-starter`** 共用根前缀 **`rocketmq`**：**`rocketmq.name-server`**、**`rocketmq.producer.*`** 为官方项；**`rocketmq.app.*`**（开关、Job/用量 topic 与消费组）由 **`RocketMqAppProperties`** 绑定，**`application.yml`** 仅保留一块 **`rocketmq:`**。（**0.1.154** 起上述应用项迁至 **`ai.rocketmq.*`**。）

### 0.1.152-SNAPSHOT

- **无遗留环境兼容**：删除 **`MilvusLegacyUrlEnvironmentPostProcessor`**、**`AiLegacyRabbitMqToRocketMqEnvironmentPostProcessor`** 及错误的 **`META-INF/spring/...EnvironmentPostProcessor`** 无扩展名占位；**`application.yml`** 移除顶层 **`milvus.config.url`**。**Milvus** 仅配置 **`com.aaron.cloud.providers.milvus.*`**（占位符仍可用 **`AI_MILVUS_*`** 等）。**RocketMQ** 统一在 **`rocketmq.*`**（**0.1.153** 起含 **`rocketmq.app.*`** 开关与 topic；此前曾写在 **`com.aaron.cloud.rocketmq.*`**）。

### 0.1.151-SNAPSHOT

- **外部化配置前缀**：原 **`ai.*`** 业务键迁移为 **`com.aaron.cloud.*`**（与 Spring Boot 文档推荐的厂商反向 DNS 前缀一致，避免占用 **`spring.*`**）；**`application.yml`** 使用 **`com.aaron.cloud`** 嵌套结构；**`@ConfigurationProperties` / `@ConditionalOnProperty` / `@Value`** 已全部对齐。已删除 **`AiLegacyProvidersEnvironmentPostProcessor`** 及对 **`ai.rag.milvus.enabled`** 的兼容映射（开发线未上线，破坏性调整可接受）。**`vector-store`** 默认值改为小写 **`milvus`**，与 **`@ConditionalOnProperty(havingValue = "milvus")`** 一致。

### 0.1.150-SNAPSHOT

- **配置说明**：**`src/main/resources/application.yml`** 以 **`#` 注释**集中说明各键默认值、覆盖用环境变量及取值含义；**`PROJECT.md`** 不再维护重复配置表，仅保留指向 **application.yml** 的一节；**`db/mysql/README.md`** 同步引用。

### 0.1.149-SNAPSHOT

- **`application.yml`**：**`com.aaron.cloud.providers.vector-store`** 默认 **`local`**；**`com.aaron.cloud.rag.retrieval-mode`** 默认 **`milvus`**；删减重复杂音注释（曾临时指向 PROJECT）。

### 0.1.148-SNAPSHOT

- **用户端对话**：用户气泡旁增加**复制提问**；RAG 命中时 SSE 在正文 token 前先下发 **`ragDoc`** 帧（按文档去重、短间隔逐条），展示「参考文档」标签进度。
- **向量库可选**：`com.aaron.cloud.providers.vector-store=local`（未接 Milvus）时，对话编排**不调 RAG**、用户端无感；**`RagQueryBridgeService`** 对检索接口短路为空；**`RagVectorInfrastructure`** 统一守卫：管理端上传/入库/索引入队/分片写向量、**开启对话检索**、**`RagIngestOrchestrationService`** 与 **RAG 类 Job** 拒绝或失败提示；新建知识库默认**不参与**对话检索。**`GET /api/v1/admin/rag-kbs/{tenantCode}/capabilities`**（**`tenantCode`** = **`sys_tenant.code`**）供管理端展示；知识库页与文档矩阵**告警 + 禁用写入按钮**。

### 0.1.147-SNAPSHOT

- **RAG 检索路径**：移除 **`mysql_lexical`** 与 MySQL **`LIKE`** 召回；**`RagRetrievalMode`** 仅保留 **`milvus`** / **`milvus_es_hybrid`**（yaml 仍写 `mysql_lexical` 时映射为 **milvus**）。**`RagQueryBridgeService`**：查询经 **`RagEmbeddingPort`** 向量化后 **`VectorStorePort#searchVectors`**；片段/引用由 **`Milvus`** 命中 `chunk_ref` 回表 **`RagChunkRepository#findCitationHitForKb`** 拼装；混合模式 **ES 不可用时仅 Milvus**（不再回退词法）。
- **`MilvusVectorStore`**：落地 **建 collection（`chunk_ref`+`embedding`）/load/insert/search/delete**，与入库 **`kb_{kbId}`** 约定一致。
- **`RagEmbeddingPort` + `RagEmbeddingService`**：`com.aaron.cloud.rag.embedding.provider`**`hash`**（与历史 SHA 派生一致）或 **`openai_compatible`**（`POST {baseUrl}/embeddings`，维数须与 **`com.aaron.cloud.providers.milvus.vector-dimension`** 一致）；入库 **`RagIngestOrchestrationService` / 分片维护** 与检索共用。
- **Elasticsearch**：**`ElasticsearchRagSearchClient#searchCitationHits`** 供混合引用侧（索引文档建议含 **`chunk_id`、`document_id`、`title`、`content`**）。
- **配置默认**：**`com.aaron.cloud.rag.retrieval-mode`** 默认 **`milvus`**；**`application.yml`** 增加 **`com.aaron.cloud.rag.embedding.*`**。

### 0.1.146-SNAPSHOT

- **RAG 词法命中**：原先对用户检索串**只取前 120 字**再 `LIKE`，长提示或关键词在句末/附件摘录时**永远扫不到分片**；改为**首部 4000 字 + 尾部 500 字**各生成一条安全子串，**OR** 匹配。对话编排检索串改为优先使用**发给模型的合并 user 正文**（含附件），重新生成时去掉固定指令前缀，仅保留「我的问题是：」后的用户问句。
- **知识库参与对话**：`listIdsWithChatRetrievalEnabled` 将 **`chat_retrieval_enabled` 为 NULL** 的存量行**视同开启**，避免未迁移列时租户下无可用库。

### 0.1.145-SNAPSHOT

- **RAG 词法检索 SQL**：`RagChunkRepository` 中 `EXISTS` 子查询原先在 **`INNER JOIN rag_document ... ON`** 里写 `d.tenant_id = rag_chunk.tenant_id`；在部分 MySQL 上会报 **`Unknown column 'rag_chunk.tenant_id' in 'on clause'`**。已改为 **`ON` 仅关联主键**，**`d.tenant_id`** 放在子查询 **`WHERE`**，与外层 **`.eq(tenantId)`** 同参绑定。
- **对话 RAG 编排**：`rag_knowledge_base` 增加 **`chat_retrieval_enabled`**（`ToggleState`，默认 **ON**）；租户内**凡开启的库**每轮合并检索并注入上下文，**全部关闭**时不走 RAG。移除 **`com.aaron.cloud.chat.rag.attach-always`** 与正文关键词触发；**`RagQueryPort`** 增加 **`searchSnippetsAcrossKnowledgeBases` / `searchCitationHitsAcrossKnowledgeBases`**（多库合并，受 **`com.aaron.cloud.rag.retrieval-mode`** 路由）。存量库执行 **`db/mysql/migrate_0_1_145_rag_kb_chat_retrieval_enabled.sql`**。

### 0.1.144-SNAPSHOT

- **可观测性**：`chatOpenStream` 增加 **INFO** 起止日志（`intent`、`ragSnippetCount`、`ragCitationCount`、`retrievalMode` 等，不落用户原文）；**`RagQueryBridgeService`** 对 snippets/citations 打 **INFO**；**`RagChunkRepository`** 词法路径打 **DEBUG**（含 LIKE 子串为空、无行、子串长度）。

### 0.1.143-SNAPSHOT

- **用户端对话页（`web/user-web`）**：「深度思考」开关**默认开启**（仍仅对支持思考的模型生效；切换至不支持思考的模型时自动关闭）。

### 0.1.142-SNAPSHOT

- **管理端对话日志**：会话详情抽屉内消息按 **「用户 + 紧随其后的助手」** 合并为**一轮卡片**（第 N 轮、上半用户区 / 下半助手区），未成对的消息单独成块并附简要说明，减少逐条跳读时的眼晕与对错轮困难。助手正文与历史稿展示抽离为 **`ChatDrawerAssistantAuditBlock.vue`**。

### 0.1.141-SNAPSHOT

- **RAG 命中统计**：`rag_document` / `rag_chunk` 增加 **`hit_count`**（`schema_v1.sql` 与存量库可选执行 **`db/mysql/migrate_0_1_141_rag_hit_count.sql`**）；对话在 **RAG 意图** 下词法召回后把引用写入助手 **`meta_json#ragCitations`**，落库成功后按引用**逐分片、逐文档**累加计数。
- **`RagQueryPort`**：新增 **`searchCitationHits`**（桥接层当前统一走 MySQL 词法，与片段拼装同源条件）；**`ChatMessageView`** 增加 **`ragCitations`** 供开放接口与管理端消息列表解析。
- **管理端**：知识库文档矩阵表增加**文档命中**列；分片抽屉与**分片管理页**展示分片/文档命中；**对话日志**抽屉对助手消息展示可点击的**知识引用标签**，弹窗拉取对应分片正文。

### 0.1.140-SNAPSHOT

- **审计事件**：`parseActorUserIdIfUser` 在 **`actorType` 为空** 时仍按数字 `actorId` 解析自然人主键（显式非 `USER` 不解析）；成员类事件「关联对象」**优先用 `detail_json.userId` 与 `sec_user_account` 批量解析的展示名**（与操作者同源），`loginName` 仅作兜底。
- **对话日志（管理端）**：列表回填 `userDisplayName` 时**校验** `(tenant_id, user_id)` 在 **`sys_tenant_member` 存在成员行**，再使用全局账号展示名，**避免跨租户误用同一 userId 的昵称**；无成员关系则不填展示名（前端显示「用户（展示名暂缺）」）。前端 **`formatChatConversationUser`**：仅 **`userId` 为空** 时用设备码访客文案。
- **公共**：`SysTenantMemberRepository.listUserIdsHavingMembership` 供上述批量校验。

### 0.1.139-SNAPSHOT

- **管理端观测列表可读性**：`adminListDisplay` 统一兼容 **camelCase / snake_case** 租户字段；**HTTP 访问日志**用户列区分已登录展示名与 **访客 · 设备码**；**计量**用户列在无展示名时回退 **设备 · deviceId**；**对话日志**用户列同访客规则，**会话总 Token（约）** 与抽屉横幅对 **0** 与 **null** 区分展示。
- **审计事件**：`SysAuditEvent` 增加 **`resourceDisplaySummary`**（成员事件从 `detail_json.loginName` 推导，工作区切换从租户名/编码推导）；列表「关联对象」主列展示该摘要，原标识移至详情「排障」；**操作者**列在 USER 且仅有数字 `actorId` 时不直接回显裸 id。
- **后端**：`SysAuditEventRepository` 注入 `ObjectMapper` 填充上述摘要（解析失败 `WARN` 日志）。

### 0.1.138-SNAPSHOT

- **管理端（`web/admin-web`）**：按 **`.cursorrules` §7.1** 扫除非主路径上的裸数字主键展示：用户管理、租户列表、CORS、MCP、网关接口/限流、知识库选择与标题、异步任务列表、对话日志列表等改为**名称 / 登录名 / `sys_tenant.code` 组合**；**`formatUserDisplayName`** 不再用 `用户 #id` 兜底；新增 **`formatTenantRowOptionLabel`** 供租户下拉文案；**`AdminLayout`** 工作区展示与创始人租户列表统一为 **名称（编码）**，去掉 `租户 ${数字}` 回退。
- **详情抽屉**：访问日志「记录编号」、计量/审计「记录编号」、任务详情「任务内部编号」等统一带**（排障）**语义；对话日志抽屉标题改为 **租户 · 标题**，内部编号单独一行。

### 0.1.137-SNAPSHOT

- **对话重试（C 端）**：重新生成时模型收到的 user 内容在「我的问题是：」前附加**结构化重写引导语**，后接用户**原始输入**（库表用户行不落库覆盖，避免多次重试叠加）。
- **管理端列表可读性**：HTTP 访问日志、审计事件、计量事件、对话日志接口补充 **租户名称 / 编码**、**用户展示名**（昵称优先）；前端列表与详情以名称为主，数字 ID 仅在「排障」项展示。
- **修订说明（规则）**：**`.cursorrules` §7.1** 增补 **「禁止主界面回显裸数字主键 / 技术 ID」**（用户端与管理端列表/详情主区须用语义化展示；ID 仅可置于明确排障/开发者次要路径）；**§8** 首条与之对齐。

### 0.1.136-SNAPSHOT

- **管理端**：审计事件、计量事件列表与详情展示**租户编码**（`tenantCode`），表头与详情用语改为中文语义；计量将**耗时并入**同一条 Token 计量记录的 `ref_json.durationMs`，去掉重复的 `MODEL_COMPLETION` 落库（历史行仍可见）。
- **管理端对话日志**：会话列表增加**会话总 Token（约）**列及详情抽屉内合计条；租户列改为编码。
- **敏感词管理**：创始人**维护目标租户**下拉移至租户扩展词库**词表下方**。
- **用户端 `/…/system/me`**：去掉「租户与路由」整段；登录态展示**登录名与昵称**（`/open/v1/system/me` 增加 `loginName`、`displayName`）。

### 0.1.135-SNAPSHOT

- **用户端 `MeView`**：**`/…/system/me`** 去掉「租户 ID」自增数字展示；主文案与兜底均以**租户编码**（及名称）为准。**`.cursorrules` §7.1** 增补「C 端租户对人展示」规则（用户可见标识为 **`sys_tenant.code`**）。

### 0.1.134-SNAPSHOT

- **用户端（`web/user-web`）路由**：第一段由 **`sys_tenant.id` 数字** 改为 **`sys_tenant.code`（租户编码）**，例如 **`/default/chat`**、**`/default/system/me`**；**`outboundTenant`** 优先 **`X-Tenant-Code`**，旧 localStorage 纯数字段仍发 **`X-Tenant-Id`**。**`vite-env.d.ts`**、**`.env.development`** 增加 **`VITE_TENANT_CODE`**。
- **网关**：**`TenantContextFilter`** 在已登录场景下，**`X-Tenant-Id` 未传**时根据 **`X-Tenant-Code`** 解析租户并校验成员身份（与 C 端路径一致）。
- **文档**：**`PROJECT.md`** 本节与 **`.cursorrules` §3.5** 路由说明已与「租户编码」对齐。

### 0.1.133-SNAPSHOT

- **前端 dev / preview**：**`web/user-web`**、**`web/admin-web`** 的 **`vite.config.ts`** 增加 **`server.allowedHosts` / `preview.allowedHosts`**，放行花生壳等穿透常见域 **`*.vicp.fun`**、**`*.vicp.cc`**，避免 Vite 6 对 `Host` 校验返回 **`Blocked request... is not allowed`**。

### 0.1.132-SNAPSHOT

- **用户端（`web/user-web`）路由与租户（★ 产品约定）**：对话入口由 **`/chat`** 改为 **`/{租户数字ID}/chat`**，**`/system/me`** 改为 **`/{租户数字ID}/system/me`**；**`/`**、**`/chat`**、**`/system/me`** 重定向至默认或本地缓存租户路径。**`outboundTenant`** 使路径租户优先于 **`localStorage`** 决定 **`X-Tenant-Id`**；**SSE `fetch`** 与 axios 共用。**移除**侧栏「工作区」下拉切换；登录/注册成功后 **`router.push`** 至 JWT 主租户 **`/{tid}/chat`**。**`PROJECT.md`** 增加 **「★ 用户端路由与租户（必读）」**专节；**`.cursorrules` §3.5** 路由示例已与用户端路径对齐。

### 0.1.131-SNAPSHOT

- **（已由 0.1.132 替代）** 曾以侧栏「工作区」下拉切换多租户；现改为 **URL 路径 `/{租户ID}/chat`** 指定租户，见上条与 **`PROJECT.md`** 专节。

### 0.1.130-SNAPSHOT

- **用户端（`web/user-web`）**：**`getOrCreateDeviceId`** 不再直接调用 **`crypto.randomUUID()`**（部分内嵌 WebView / 旧环境不可用）；改为优先 **`randomUUID`**，否则 **`getRandomValues`** 组 UUID v4，最后再 **`Math.random`** 兜底。

### 0.1.129-SNAPSHOT

- **用户端（`web/user-web`）**：**`/open/**` 在携带非法 `Authorization` 时仍被 Spring OAuth2 判 401**（与 `permitAll` 并存时的常见行为）。**`localhost:5173` 与 `本机IP:5173` 不同源**，`localStorage` 独立，用 IP 打开时易残留无效 **`ai_user_access_token`** 导致首屏「加载模型列表失败」。**`http.ts`** 增加响应拦截器：**401 且路径含 `/open/`** 时 **`clearUserSession()`** 后**重试一次**；**`resolveApiBaseForBrowser`** 在非 **`import.meta.env.PROD`** 时生效（含 **`vite preview`**）。**`ChatView`** 模型列表失败提示改用 **`apiRequestErrorMessage`**。

### 0.1.128-SNAPSHOT

- **用户端（`web/user-web`）**：开发态下若页面通过**局域网 IP/非 loopback 主机名**打开，而 **`VITE_API_BASE`** 仍指向 **`localhost` / `127.0.0.1:8080`**，浏览器会把 API 发到访问者本机而非开发机；现通过 **`resolveApiBaseForBrowser()`** 自动改为**同源 + Vite 代理**（`http.ts` 请求拦截器 + **`chat.ts`** 内 SSE **`fetch`** 与 axios 一致）。

### 0.1.127-SNAPSHOT

- **联调说明**：**`web/user-web` / `web/admin-web`** 的 **`.env.development`** 补充 **`VITE_API_BASE`** 与「IP 访问 / 跨域」关系说明（留空走 Vite 代理一般无浏览器 CORS；填 **`http://…:8080`** 直连后端则与前端不同端口，需在 **跨域来源** 登记与页面一致的 **`http://本机IP:5173`** 或 **`:5174`**）。

### 0.1.126-SNAPSHOT

- **局域网 dev（简化）**：保留 **`web/user-web` / `web/admin-web`** 的 Vite **`server.host: true`**。**撤销** **0.1.125** 引入的 **`com.aaron.cloud.cors.allowed-origin-patterns`** 与 **`DynamicCorsConfiguration`** 模式合并（避免配置与语义分叉）。从局域网 IP 访问 **`http://…:5173` / `:5174`** 时，浏览器会带对应 **`Origin`** 调 **`/api`、`/open`**，须在 **管理端 · 跨域来源**（表 **`gw_cors_allowed_origin`**）增加**一条精确 Origin**；根因是 **CORS + `credentials`** 下不能用 `*`，且 **`localhost` 与 IP 在协议里算不同站点**。

### 0.1.125-SNAPSHOT

- **局域网访问开发前端**：**`web/user-web`**、**`web/admin-web`** 的 Vite **`server.host: true`**。**（已由 0.1.126 撤回）** 曾增加 **`com.aaron.cloud.cors.allowed-origin-patterns`** 与 **`DynamicCorsConfiguration`** 模式合并，现改为仅保留 Vite 监听与上条 **0.1.126** 所述运维方式。

### 0.1.124-SNAPSHOT

- **对话输入护栏命中**：用户消息 **落库原文**（`meta_json` 仍含 **`inputGuardBlocked`**），不再用占位句覆盖 **`content`**；C 端流式回调不再把用户气泡改写为「未发送至模型」。模型仍不接收该条；**重新生成**路径对已存原文再次跑护栏，命中则保持拒绝。

### 0.1.123-SNAPSHOT

- **管理端敏感词 API 与列表**：**`SensitiveTermRow`** 对外返回 **`tenantCode`**（`sys_tenant.code`，与数据租户下拉括号内编码一致），**不再**返回 **`tenantId`**；**`SysTenantRepository.mapTenantCodeByIds`** 批量解析避免 N+1。前端租户扩展表列改为 **「租户编码」**，缺省显示 **—**。

### 0.1.122-SNAPSHOT

- **管理端敏感词**：分页器移出表格滚动区，固定在 **各分栏卡片底部**（顶部分隔线 + 右对齐），仅表格区域纵向滚动，避免分页紧贴末行数据。

### 0.1.121-SNAPSHOT

- **管理端敏感词**：**`/chat/sensitive-terms`** 由上下堆叠改为 **左右分栏**（平台强制词库 / 租户扩展词库），窄屏（约 960px 以下）自动改为纵向堆叠；表体区域在各自栏内滚动。

### 0.1.120-SNAPSHOT

- **管理端用户页合并**：**`/users`** 单页整合原 **租户成员**（邀请、移出、在册/已退出筛选、跨租户筛选）与 **本租户账号**（新增、启停、踢下线、封禁、删除）；侧栏移除 **`/users/roles`**，旧地址重定向至 **`/users`**。「含已退出成员」改为 **复选框**，避免原开关两侧文案宽度变化导致工具栏按钮抖动。

### 0.1.119-SNAPSHOT

- **网关接口目录与限流（后端 + 管理端）**：新增表 **`gw_api_endpoint`** 与 **`/api/v1/admin/gateway-api-endpoints`**（分页、picker、增删改）；**限流分页**支持创始人 **`rateScope=GLOBAL`**（仅 `tenant_id` 为空）或 **`rateScope=TENANT` + `tenantId`**，非创始人仍固定当前 JWT 租户。**`接口与限流`** 页改为 **Tab：接口管理 / 限流控制**；限流表单支持从目录 **快捷选择**路径与方法；**`db/mysql/migrate_0_1_119_gw_api_endpoint.sql`** 供存量库增量。
- **修订说明（文档与脚本）**：**`.cursorrules` §4.1.3** 增加 **`gw_api_endpoint`** 维护约定；新增 **`db/mysql/gw_api_endpoint_catalog_inserts.sql`** 为全项目对外 REST 的 **`INSERT IGNORE`** 清单与后续追加真源；**`db/mysql/README.md`** 已索引该文件。

### 0.1.118-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：异步任务与「入库任务」展开区将 **任务类型、状态、执行阶段、入参/结果** 等改为中文摘要（**`utils/ragJobDisplay.ts`** 统一映射）；列表 **结果摘要** 列可读化；原始 JSON 收入折叠区并标注排障用途；文档列表未知 **展示状态** 枚举时不再裸显英文码。

### 0.1.117-SNAPSHOT

- **`pom.xml`**：显式加入 **`commons-io` 2.18.0**，满足 Apache Tika（OOXML/zip 检测等）对 **`ChecksumInputStream`** 的要求，修复管理端 RAG 文档上传解析时 **`NoClassDefFoundError: org/apache/commons/io/input/ChecksumInputStream`**（旧版 commons-io 被其它依赖抢先解析时会出现）。

### 0.1.116-SNAPSHOT

- **管理端对话日志**：历史稿区域将 **「历史稿归档」说明与折叠标题合并**（`el-collapse-item` 自定义 `#title`），不再单独一块说明 +「展开查看各版内容」两层文案。

### 0.1.115-SNAPSHOT

- **管理端对话日志（`ChatConversationsView`）**：含 **`priorVersions`** 的助手消息改为**先「当前版本」**（蓝左边线、白底卡片、「当前版本」标签 + 模型）**后「历史稿归档」**（灰底虚线框、说明文案、折叠内各版 **「已替换」** 琥珀标签），避免历史正文与当前生效稿视觉混淆。

### 0.1.114-SNAPSHOT

- **重新生成与多版持久化（用户端 + 管理端）**：`POST .../retry` 请求体携带 **`modelAlias` / `thinkingEnabled`**，与当前选择器一致，避免换模型后仍走旧模型；历史助手稿写入 **`meta_json.priorVersions`**，**`GET .../messages`**（开放与管理端）经 **`ChatMessageView.priorVersions`** 返回。**`ChatView`** 从历史 **`priorVersions`** 恢复 **`replyVariants`**，刷新后仍可切换箭头；**`chatAdmin` / 对话日志抽屉**展示可折叠的历史版（模型、思考、正文、用量）。

### 0.1.113-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：**`thread-head`** 与 **`.messages` / `.composer-surface`** 统一 **`max-width: 58rem` + 水平居中**，侧栏存在时标题与对话、输入区同列对齐，减轻「整块对话偏右」观感；**`composer`** / **`composer-note`** 水平 **padding** 与消息区 **24px** 对齐；**平板**侧与 **`.messages`** 一致为 **14px**；**手机**恢复顶栏全宽（取消 **max-width**）。

### 0.1.112-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：复制 **`split-button`** 去掉 **`msg-copy-group`** 外框与底色；隐藏 **`el-dropdown__caret-button::before`** 竖分隔线并收紧左右 **padding**，主键与下拉箭头更贴近原生 **select** 观感。

### 0.1.111-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：**重新生成**后保留上一版与新版正文，操作条增加 **「上一版 / 当前序号 / 下一版」** 箭头切换（与参考图一致的紧凑计数）；服务端仍只保留最新助手行，历史版仅存于**当前会话前端**；仅**最新一版**可点赞/点踩（旧版无落库 id）。**复制**：主键与下拉副键外包 **`msg-copy-group`** 便于样式收口（外观细节见 **0.1.112**）。

### 0.1.110-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**`KbDocumentMatrixPanel`** 文档表 **`ResizeObserver`** 原绑定在包裹 **`el-table`** 的 **`docs-table-wrap`** 上，将读得的高度回写为表格 `:height` 后，表格实际占位略大于 flex 槽位会撑高该 wrap，触发观测器循环增高，页面持续向下扩展、分页不可见；改为观测 **`docs-main`**，用主列高度减去工具栏、底栏与 **`gap`** 计算表体高度，打破反馈环。

### 0.1.109-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：助手消息「模型」与操作条合并为同一行（操作条在模型名之后）；复制主按钮仅保留图标；下拉「复制为 Markdown」去掉副标题；操作条容器去掉底色与边框，与模型行视觉一体。

### 0.1.108-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**`KbDocumentMatrixPanel`** 侧栏「全部分类」按钮误用 **`.cat-item { flex: 1 }`** 在纵向 **`.cat-nav`** 中被拉高占满剩余高度；改为 **`flex: 0 0 auto`**，横向分类行仍由 **`.cat-item-grow`** 承担 **`flex: 1`**。

### 0.1.107-SNAPSHOT

- **用户端对话（`web/user-web`）**：**`ChatView`** 助手消息底部操作条改为**浅色圆角工具条**（渐变底、细描边、轻阴影）；**复制**使用 **`el-dropdown` `split-button`**（主键「复制」+ 副键展开「复制为 Markdown」双行说明）；赞/踩改为 **星标 + 精简拇指线宽**与更柔和的选中底色；分享弹窗内 **只读多行框** 与标题区样式优化；下拉 **`popper-class`** 圆角与条目悬停统一。

### 0.1.106-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**`KbDocumentMatrixPanel`** 文档区 **flex 占满剩余高度**（无数据时表体区域仍铺满）；**`ResizeObserver`** 驱动 **`el-table` 固定高度**；左侧分类栏 **`align-self: stretch`** + 分类列表可滚动；**`RagKnowledgeBasesView`** 中 **`kc-hub-body` / `kc-hub-main`** 与 **`main-hdr`** 调整（**`overflow: hidden`**、**`flex-shrink: 0`**）以配合纵向撑满。

### 0.1.105-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**`KbDocumentMatrixPanel`** 文档与入库工作台右侧**去掉内嵌「入库异步任务」表**；主区域仅保留文档表与分页；工具栏增加 **「入库任务」** 打开弹窗查看任务列表与展开详情；**「刷新」** 仅刷新分类与文档列表，任务列表在弹窗内单独刷新；切换知识库与提交入库后仍同步任务缓存以支撑轮询。

### 0.1.104-SNAPSHOT

- **管理端按租户筛选（创始人）**：**`GET /api/v1/admin/access-logs`、`audit-events`、`metering-events`** 与 **`GET /api/v1/admin/chat/conversations`** 增加可选查询参数 **`filterTenantId`**；创始人不传或清空表示**不按租户过滤（全量）**，传入则仅该租户；非创始人传其它租户 **403**。解析集中 **`AdminQueryTenantSupport`**（**`common.security`**）；**`SysHttpAccessLogRepository` / `SysAuditEventRepository` / `MeteringUsageEventRepository` / `ChatConversationRepository`** 增加 **`pageForAdmin`**。（**意图识别** **`/api/v1/admin/chat/intents`** 不在此列，见 **0.1.207**：仅当前工作区租户。）
- **管理端对话消息**：**`listConversationMessagesForAdmin`** 先 **`findByIdForAdmin`** 再按会话 **`tenantId`** 拉消息；非创始人仅允许查看本会话所属租户（与 JWT 租户一致），避免创始人换工作区后会话与消息租户不一致。
- **敏感词**：**`GET .../sensitive-terms/platform` 与 `.../tenant`** 分页 + 可选 **`q`**；租户池可选 **`filterTenantId`**（创始人指定数据租户，缺省 JWT 工作区）；**`POST`** / **`import`** 体可选 **`targetTenantId`**（创始人写他租扩展池）；删除租户池词时创始人可删任意租户行。已上线库按需执行 **`migrate_0_1_104_admin_tenant_filter_sensitive_pages.sql`**；**`schema_v1.sql`** 网关限流种子同步新 GET 路径。
- **管理端 UI**：**`AccessLogsView` / `AuditEventsView` / `MeteringView` / `ChatConversationsView`** 创始人侧增加租户下拉（可清空=全量）；**`ChatSensitiveTermsView`** 双表分页 + 关键词筛选 + 固定高度滚动；**`useAdminFounderTenantOptions` / `useAdminFounderListTenantFilter`**（**`web/admin-web/src/composables`**）；**`admin.ts` / `chatAdmin.ts`** 请求参数对齐。

### 0.1.103-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**RagKnowledgeBasesView** 将知识库切换从左侧列表改为**标题「知识库」旁的可搜索下拉**（`el-select` + 选项双行展示），主内容区单列铺满；**`global.css`** 中知识库卡片体 flex 选择器由 **`kc-hub-split`** 改为 **`kc-hub-body`**。

### 0.1.102-SNAPSHOT

- **用户端对话（`web/user-web`）**：助手消息底部改为**图标操作条**（复制 + 下拉「复制为 Markdown」、赞/踩互斥与蓝/红态、分享弹窗、**重试**、更多菜单）；**`chat.ts`** 增加 **`streamRegenerateAssistantReply`**；发送结束后 **`loadMessagesForConv`** 同步服务端消息 id，便于评价与重试。
- **开放对话 API（`chat` + `common`）**：**`ChatMessageUserFeedback`** 增加 **`LIKE`**；**`POST .../messages/{assistantMessageId}/retry`**（SSE）在删除最后一条助手消息后按前一条用户消息**不重复插入 user** 再流式生成；**`ChatMessageRepository`** / **`LnkChatConversationMessageRepository`** 增加按主键删除以支撑重试；抽取 **`openAssistantSseStream`** 复用发送与重试的模型流逻辑。

### 0.1.101-SNAPSHOT

- **用户端对话（`web/user-web`）**：**ChatView** 助手气泡在 flex 布局中增加 **`min-width: 0`** 与 **`flex: 1 1 0%`**（上限仍 **`min(100%, 900px)`**），使消息列宽度不随「思考过程」展开或超长无空格片段而突然撑满；思考区增加 **`overflow-wrap: anywhere`** 与宽度约束，避免长串撑破布局。

### 0.1.100-SNAPSHOT

- **管理端知识中心（`web/admin-web`）**：**KnowledgeCenterLayout** 增加可点击面包屑（知识中心 → 知识库 / 异步任务 / 文档与入库 / 高级设置 / 分片管理）；去掉渐变顶栏，与子页统一为与「模型管理」一致的 **`el-card` + `.hdr` / `.title` / `.sub`** 标题区；**`global.css`** 为知识中心嵌套路由补齐 **`.page` + `.panel` 纵向占满 `el-main`** 的 flex 链，异步任务、分片页、知识库首页主体区随主内容区伸展。

### 0.1.99-SNAPSHOT

- **用户端对话（`web/user-web`）**：**ChatView** 主内容区加宽（消息列 / 输入区 / 底注 **`max-width` 48rem→58rem**；气泡 **`720px→900px`**；顶栏说明 **42rem→54rem**；空状态欢迎与快捷提示同步放宽）。

### 0.1.98-SNAPSHOT

- **用户端对话（`web/user-web`）**：思考开关文案改为 **「思考」**；**更浅**蓝底与浅灰未选态、**胶囊圆角**（`border-radius: 999px`）；`thinkingEnabled` 语义不变。
- **管理端知识中心（`web/admin-web`）**：**`/knowledge-center/knowledge-bases`** 在 **`KnowledgeCenterLayout`** 下取消 **`kc-main` 1100px 限制**，顶栏内层与说明同宽铺满；**`RagKnowledgeBasesView`** 顶栏「异步任务 / 刷新列表 / 新建知识库」统一为默认尺寸、**次要操作均为 `plain`**，与主按钮区分一致。

### 0.1.97-SNAPSHOT

- **用户端对话（`web/user-web`）**：**深度思考** 改为单行**文字按钮**，点击在**开启**（主色蓝底白字，与 Element Plus 主按钮一致）与**关闭**（灰色文案）之间切换；`thinkingEnabled` 与后端 **`thinkingEnabled`** 字段语义不变。
- **管理端知识中心（`web/admin-web`）**：**`/knowledge-center/knowledge-bases`** 为文档与入库主入口（左栏选库、右栏 **`KbDocumentMatrixPanel`**：筛选、查询、触发索引、上传与分片策略）；**`DocumentChunksManageView`** 面包屑「文档管理」与返回跳转 **`/knowledge-center/knowledge-bases?kbId=`**；**`KnowledgeCenterLayout`** 顶栏说明与上述流程一致。

### 0.1.96-SNAPSHOT

- **RAG 分片检索开关**：**`rag_chunk.retrieval_enabled`**（**`RagChunkRetrievalEnabled`**）；词法检索 **`RagChunkRepository.searchSnippetTextsByLexical`** 仅命中 **ENABLED**；管理端 **PATCH 分片** 支持 `content` / `retrievalEnabled`；**DELETE 单分片**、**POST 追加分片**、**POST 与下一块合并**；**`GET .../rag-kbs/{tenantCode}/{kbId}/document/{docId}`** 单文档视图（避免与 `documents/page` 路径冲突）。已上线库执行 **`migrate_0_1_96_rag_chunk_retrieval_enabled.sql`**。
- **管理端**：路由 **`/knowledge-center/workspace/:kbId/documents/:docId/chunks`**，**`DocumentChunksManageView`**（左卡片网格 + 分片内搜索高亮 + 右栏文档元数据与附件占位；工作台文档名链入）；**`KnowledgeCenterLayout`** 分片页加宽；**`ragAdmin`** 对齐新 API。

### 0.1.95-SNAPSHOT

- **RAG 知识库文档矩阵**：表 **`rag_kb_document_category`**；**`rag_document`** 增加 **`category_id`、`display_status`、`applicable_scope`、`uploaded_by_user_id`**；默认分类种子与分页/筛选 API（**`GET .../document-categories`**、**`GET .../documents/page`**、**`PATCH .../documents/{id}`**、**`GET .../documents/{id}/markdown`** 等）；**`RagKbAdminApplicationService`** 分类 CRUD、文档 PATCH、Markdown 导出；已上线库执行 **`migrate_0_1_95_rag_doc_category_matrix.sql`**。
- **管理端**：**`KnowledgeBaseWorkspaceView`**「文档与入库」改为**左栏分类 + 右栏检索/矩阵表/分页**，状态差异化操作与分片抽屉；**`ragAdmin.ts` / `types/admin.ts`** 契约对齐。

### 0.1.94-SNAPSHOT

- **敏感词管理**：表 **`guardrail_sensitive_term`**（**`PLATFORM`** 全租户强制池 `tenant_id=0` + **`TENANT`** 本租户扩展）；**`ChatInputGuardService`** 与配置项 **`com.aaron.cloud.chat.input-guard.sensitive-words`** 合并命中；管理端 **`GET/POST/DELETE /api/v1/admin/chat/sensitive-terms*`**（**`GuardrailSensitiveTermAdminService`**：平台池 **仅创始人** 可增删改与批量导入，租户池仅当前租户）。
- **管理端 UI**：**`/chat/sensitive-terms`**（菜单权限同 **`CHAT`**）。
- **入库时间**：**`CommonMetaObjectHandler`** 与 **`spring.jackson.time-zone`** 统一为 **`Asia/Shanghai`**（东八区墙钟）；**`db/mysql/schema_v1.sql`** 头部说明与 **`guardrail_sensitive_term`** 列注释同步；已上线库执行 **`migrate_0_1_94_guardrail_sensitive_term.sql`**。

### 0.1.93-SNAPSHOT

- **对话输入护栏**：**`ChatInputGuardService`** 在发消息前校验有效性（长度、低信息噪声）、**敏感词子串**（`com.aaron.cloud.chat.input-guard.sensitive-words`）、**提示词攻击特征正则**（可配置，内置常见越狱句式）、租户 **`guardrail_rule`**；命中时 **SSE** 下发 **`inputBlocked`** 帧并以固定劝导语作为助手回复落库（用户行占位文案，不存原文）。**`AiChatInputGuardProperties`**（`com.aaron.cloud.chat.input-guard`）；原 **`EvalApplicationService`** 同步正则护栏已并入本服务并删除该类。
- **点踩**：**`POST /open/v1/chat/conversations/{id}/messages/{messageId}/feedback`**，`meta_json.userFeedback` 为 **`DISLIKE`/`NONE`**；**`ChatMessageView.userFeedback`**；用户端 **ChatView** 点踩可取消。
- **用户端 UI**：「思考」由开关改为 **关/开** 分段按钮选中态；流式解析支持 **`inputBlocked`**。
- **管理端 · 知识中心**：侧栏「AI 中台」下 **知识中心** 改为**单入口**（不再拆「知识库 / 文档入库 / 异步任务」子菜单）；`/knowledge-center/ingest` 重定向至知识库列表；**异步任务**仍保留独立路由并在列表页提供入口。
- **布局与 UI**：`KnowledgeCenterLayout` 顶栏采用 **青绿渐变** 与说明文案；工作台 **文档与入库** 合并展示 **异步任务行 + 已入库文档**，状态含等待中 / 执行中 / 已完成 / 失败；**添加内容** 抽屉支持 **网页爬取、本地上传、Markdown 任务** 及可选 **分片策略**。
- **RAG API**：`POST .../documents/upload` 增加可选 **`chunkStrategy`**；**`FileIngestJobRequest`** 增加 **`chunkStrategy`** 并入队 payload，与网页任务一致。

### 0.1.92-SNAPSHOT

- **LLM 用量**：**`LlmModelUsageRecorder`** 统一在产生 usage 后执行 Redis 额度增量 + **`llm_model.tokens_used`** + **`metering_usage_event`**；**任意 `model_kind`** 与语言模型共用「每行模型配置」的额度语义。
- **计量类型**：**`MeteringMeterType.LLM_MODEL_USAGE`**（`llm.model.usage`）承载非对话类 token；对话仍为 **`LLM_CHAT_COMPLETION`**。
- **`LlmUsageDigestMessage`** 增加 **`meterTypeCode` / `modelKindCode`**（兼容旧队列缺省为对话计量）；**`LlmUsagePersistenceService`** 写入 `ref_json.modelKind`。
- **管理端**：可配置模型页说明文案与「仅语言走对话流」区分，避免误解为只有语言模型计 token。

### 0.1.91-SNAPSHOT

- **数据库**：**`llm_model`** 增加 **`model_kind`**（`LANGUAGE` / `SPEECH` / `VISION` / `VECTOR` / `SMART_ROUTING`，默认 `LANGUAGE`）；已上线库执行 **`db/mysql/migrate_0_1_91_llm_model_kind.sql`**，空库以 **`schema_v1.sql`** 为准。
- **枚举与策略**：**`LlmModelKind`**；**`LlmModelKindPolicy`** 约束 **OpenAI Chat 流式 / C 端发消息** 仅 **`LANGUAGE`**；**`listForCatalog`** 仅列出语言模型；**`RagKbAdminApplicationService.patchSettings`** 绑定知识库模型时仅允许语言模型。
- **管理端**：**`LlmModelAdminView`** 与表单支持模型类型；**`web/admin-web`** 模型页展示类型下拉；知识库工作台模型下拉仅展示语言模型（与后端校验一致）。

### 0.1.90-SNAPSHOT

- **数据库**：**`rag_knowledge_base`** 增加默认分片策略、固定字数、滑动重叠、**`assigned_llm_model_id`**；**`rag_document`** / **`rag_chunk`** 增加逻辑删除与来源、长度、文件名等元数据；已上线库执行 **`db/mysql/migrate_0_1_90_rag_knowledge_workspace.sql`**，空库以 **`schema_v1.sql`** 为准。
- **枚举与分片**：**`RagChunkStrategy`**（不分片 / 固定字数 / 语义段落 / 滑动窗口 / 自定义预留）、**`RagDocumentSourceType`**；**`RagChunkSplitter`** + **`RagIngestOrchestrationService`**：网页 **HTTP 拉取 → Jsoup 近似 Markdown → 分片 → MySQL 落库 → `RagEmbeddingPort` 向量化 → `VectorStorePort` 写入 Milvus（`kb_{kbId}`）**；文件任务支持 **`markdownContent`** 正文；**`JobTaskExecutionService`** 委托编排，**`result_json`** 含 **`steps`** 时间线。
- **检索**：**`RagChunkRepository`** 词法路径 **排除** 已逻辑删除文档与分片，避免「删了仍命中」。
- **向量删除**：**`VectorStorePort#deleteChunkVectors`**：`NoOp` 为空实现；**`MilvusVectorStore`** 按 `chunk_ref` 表达式删除。
- **管理端 API**：**`PATCH /api/v1/admin/rag-kbs/{tenantCode}/{kbId}/settings`**；**`GET/DELETE .../documents`**、**`GET/PATCH .../chunks`**、**`POST .../documents/upload`**（Tika 抽取）；**`RagKbAdminView`** 扩展策略与绑定模型字段；网页入库请求可选 **`chunkStrategy`**。
- **管理端 UI**：路由 **`/knowledge-center/workspace/:kbId`** 工作台（设置、文档列表、分片查看与编辑、上传）；知识库列表增加 **工作台** 入口与策略/模型列；**文档入库** 支持单次分片覆盖与 Markdown 粘贴；**异步任务** 详情展示 **`steps`** 时间线。
- **依赖**：**`org.jsoup:jsoup`**（HTML→正文/Markdown 近似）。

### 0.1.89-SNAPSHOT

- **管理端 · 知识中心**：侧栏「AI 中台」下将原「RAG 知识库」改为 **知识中心** 子菜单（**知识库** / **文档入库** / **异步任务**），路由前缀 **`/knowledge-center/*`**；旧地址 **`/rag/knowledge-bases`** 重定向至新路径；菜单码 **`RAG_KBS`** 展示名改为「知识中心」。
- **文档入库页**：选择知识库后可提交 **网页 URL 入库**、**文件元数据入库**（调用既有 `POST .../url-import-jobs`、`POST .../file-ingest-jobs`），并引导至异步任务查看进度。
- **异步任务页**：调用 **`GET /api/v1/admin/job-tasks`**（分页，可选 **`taskType`**）展示本租户 `job_task` 记录；详情弹窗展示 `payloadJson` / `resultJson`。
- **后端**：新增 **`AdminJobTaskRestController`** + **`JobTaskAdminApplicationService`**；**`JobTaskExecutionService`** 对 **`RAG_URL_IMPORT`**、**`RAG_FILE_IMPORT`** 做校验并以结构化 **`result_json`** 标记流水线占位阶段（与 **`RAG_INDEX`** 一致，后续可接爬取 / file / Milvus）。

### 0.1.88-SNAPSHOT

- **运维**：默认 **`management.health.elasticsearch.enabled=false`**（可用 **`MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED`** 覆盖），避免未运行 Elasticsearch 时 **Actuator `ElasticsearchRestClientHealthIndicator`** 在 JMX/health 探活下反复 **Connection refused WARN**；与 **`com.aaron.cloud.rag.elasticsearch.enabled`** 解耦——**业务不要求必须起 ES**；RAG 默认 **`milvus`**，仅混合模式按需启用 ES。

### 0.1.87-SNAPSHOT

- **Elasticsearch RAG 客户端**：**`RestClient.builder`** 仅接受 **`org.apache.http.HttpHost`**（elasticsearch-rest-client 与 HttpClient 4），修正误用 **`org.apache.hc.core5.http.HttpHost`** 导致的编译错误。

### 0.1.86-SNAPSHOT

- **RAG / Elasticsearch 启动**：**`ElasticsearchRagSearchClient`** 改为 **`ElasticsearchRagClientEnabledCondition`** 装配——仅当 **`com.aaron.cloud.rag.elasticsearch.enabled=true`** 且 **`config.host-ports` 非空**（**0.1.198** 起；此前曾为 **`uris` 非空**）时才注册 Bean，避免启用 ES 但节点未配时构造器抛错导致**整应用无法启动**；`MILVUS_ES_HYBRID` 下 ES 不可用时仍走既有降级（`ObjectProvider#getIfAvailable`）。

### 0.1.85-SNAPSHOT

- **RAG 检索模式**（`com.aaron.cloud.rag.retrieval-mode` / `RagRetrievalMode`）：**`milvus`**（Milvus 近似检索 + MySQL 分片元数据拼装）、**`milvus_es_hybrid`**（Milvus + Elasticsearch `match` 关键词合并去重；引用侧 ES 命中需索引含 **`chunk_id`** 等字段，见 **`ElasticsearchRagSearchClient#searchCitationHits`**）。向量化由 **`RagEmbeddingPort`**（`hash` 或 **`openai_compatible`**）与 **`com.aaron.cloud.providers.milvus.vector-dimension`** 对齐。统一由 **`RagQueryBridgeService`** 实现 **`RagQueryPort`**。
- **Elasticsearch（可选）**：依赖 **`co.elastic.clients:elasticsearch-java`**；`com.aaron.cloud.rag.elasticsearch.enabled=true` 且 **`config.host-ports` 非空** 时启用（见 **0.1.86** 条件类，**0.1.198** 起无 **`uris`**）；索引字段约定 **`tenant_id`、`kb_id`、`content`**（默认索引名 **`rag_agent_documents`**，与对端默认一致）。产品路径仍以 **Milvus** 为唯一向量库；ES 仅作 **BM25/全文** 侧车，与 `.cursorrules` 中「禁止并列第二套向量库」不冲突。
- **用户画像与对话**：表 **`ten_profile_tag`**（迁移脚本 **`db/mysql/migrate_0_1_85_ten_profile_tag_rag_modes.sql`**）；**`UserProfileApplicationService`** 在用户发言落库后更新轮次与最近摘要，**`ChatApplicationService`** 在首条 system 前注入 **「【用户画像…】」** 追加段。

### 0.1.84-SNAPSHOT

- **管理端对话日志**：**`ChatConversationsView`** 抽屉内助手消息将 **「思考过程」** 置于 **模型行之后、Markdown 正文与 token 用量之前**，与推理先于最终回复的阅读顺序一致，并微调思考区样式。

### 0.1.83-SNAPSHOT

- **管理端租户隔离**：**`GET /api/v1/admin/users`** 改为仅分页返回**当前租户** **`sys_tenant_member`** 行（不再 **`sec_user_account` 全表分页**）；**`GET /api/v1/admin/users/{id}`**、**`PUT`**、**`DELETE`**、**`.../admin-menus`** 在目标用户**非本租户成员**时 **403**（**`PUT .../tenant-role`** 在未带 **`tenantId`** 覆盖时同判）。**网关限流**列表对创始人亦仅 **`gw_api_rate_limit_rule.tenant_id = 当前上下文租户`**，且不再混入 **`tenant_id IS NULL`** 的全局规则行。**CORS 允许来源**（表无 `tenant_id`）管理端 **GET/POST/PUT/DELETE** 仅限 **创始人**。**`AccessLogFilter`** 改为 **`FilterRegistrationBean` order 11**（紧随 **`TenantContextFilter` 10**），避免过滤器链顺序导致落库时 **`TenantContextHolder` 已清空**、**`tenant_id` 长期为空**进而观测列表串租的问题。
- **管理端用户列表页**：去掉「全部用户 / 仅本租户成员」开关，与后端列表语义一致。

### 0.1.82-SNAPSHOT

- **成员邀请**：**`POST /api/v1/admin/tenant-members`** 请求体由 **`userId`** 改为 **`loginName`**（与 **`sec_user_account.login_name`** 及 **`uk_sec_user_login_name`** 一致）；**`TenantMemberRoleApplicationService.inviteMember`** 按登录名解析用户；审计 **`TENANT_MEMBER_INVITE`** 的 **`detail_json`** 增加 **`loginName`** 键。
- **管理端**：**「成员角色」**邀请弹窗改为填写**登录名**。

### 0.1.81-SNAPSHOT

- **账号 API**：移除对 JSON 键 **`username`** 的 **`@JsonAlias`** 兼容；登录/注册与管理端创建、更新用户请求体仅接受 **`loginName`**（与未上线前提一致）。

### 0.1.80-SNAPSHOT

- **`sec_user_account`**：**`username`** 列重命名为 **`login_name`**（登录名）；**`display_name`** 注释明确为昵称/展示名；**`id`** 改为应用侧 **MyBatis-Plus `ASSIGN_ID`（雪花 `Long`）**，库表不再 **`AUTO_INCREMENT`**。已上线库执行 **`db/mysql/migrate_0_1_80_sec_user_account_login_name_assign_id.sql`** 一次。
- **API**：管理端 **`UserView`**、**`TenantMemberRow`**、**`AdminMeView`** 与开放 **`/open/v1/auth/login`、`/register`** 请求体字段为 **`loginName`**（无旧 **`username`** 别名）。冲突异常 **`ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT`** → **`LOGIN_NAME_CONFLICT`**（409，中文「该登录名已被占用」）。
- **前端**：管理端与用户端登录/注册与用户列表文案改为「登录名 / 昵称」；**`jwtSubject`** 注释与 **`adminMe`** 类型同步。

### 0.1.79-SNAPSHOT

- **租户系统参数**：**`TenantRuntimeSettingApplicationService.replace`** 在启用 Redis 时对每个变更键执行**缓存双删**（写库前 `evict`、持久化后再 `evict`）；**`AdminTenantRuntimeSettingsRestController`** 与 **`TenantRuntimeSettingRedisCache`** 注释说明「仅 JWT 当前租户、无跨租户请求体」。
- **管理端**：**系统参数**页（原运行参数）文案强调仅本租户、保存即生效与双删策略；侧栏与面包屑与 **`adminMenuCodes`** 展示名与「系统参数」对齐；非布尔类项使用可编辑输入框（枚举扩展后可直接改值）。
- **配置**：**`application.yml`** 注释与上述行为对齐。

### 0.1.78-SNAPSHOT

- **`.cursorrules` §7.2（新）**：明确本仓库「规则文档」指根目录 **`.cursorrules`**；**重点逻辑须注释**；**每次修改相关逻辑须同步更新注释**，且若存在 **`PROJECT.md` 专节**须同变更集修订；§9 协作清单增加对 §7.2 的引用。
- **`PROJECT.md`**：概述中区分 **`.cursorrules` §7.2**（注释与同步义务）与成员审计**专节**（字段级契约）；**`TenantMemberRoleApplicationService`**、**`AdminTenantMembersRestController`**、**`AdminAuthContextRestController`**、**`GlobalExceptionHandler`** 的 Javadoc 与 §7.2 / 专节交叉引用对齐。
- **前端错误提示**：**`web/admin-web`** 与 **`web/user-web`** 各新增 **`apiRequestErrorMessage`**，从 Axios 响应体读取 **`ApiErrorResponse.message`**，避免业务 4xx 仅显示「Request failed with status code …」；管理端成员/用户、租户、网关、菜单、运行参数、模型、**`JsonInspector`** 与用户端 **「设备与登录」**、对话页上传/发送失败提示统一走该解析。

### 0.1.77-SNAPSHOT

- **文档**：在 **`PROJECT.md`** 增加专节 **「管理端租户成员与审计写入规则（约定）」**（权限矩阵、目标租户解析、成员状态与 **`EX_MSG_*`** / **`GlobalExceptionHandler`** 契约、**`sys_audit_event`** 字段与 **`detail_json`** 键、审计失败策略）。
- **代码注释**：在 **`TenantMemberRoleApplicationService`**、**`AdminTenantMembersRestController`**、**`AdminAuthContextRestController`**、**`GlobalExceptionHandler`**（租户成员相关分支）补充与上述文档对齐的说明。

### 0.1.76-SNAPSHOT

- **租户成员（管理端）**：**`GET /api/v1/admin/tenant-members`** 默认仅 **ACTIVE**；查询参数 **`includeInactive=true`** 仅**所有者/创始人**可用；**`POST`** 邀请或恢复成员（请求体 **`loginName`** + **`role`**，按登录名解析 **`sec_user_account`**；**`DELETE /{userId}`** 仍按用户 id 从租户移除（成员行置为已退出）；**改角色**与移除时拒绝 **非 ACTIVE** 成员行（错误码 **`TENANT_MEMBER_INACTIVE`**）；重复在册邀请返回 **`TENANT_MEMBER_ALREADY_ACTIVE`**（**409**）。
- **`TenantMemberRoleApplicationService`**：与 **`SysTenantMemberRepository.listByTenantAndRoleAndStatus`** 对齐；成员邀请/移除/改角色写 **`sys_audit_event`**；**`POST /api/v1/auth/admin-context`** 成功换发 JWT 记 **`ADMIN_CONTEXT_SWITCH`** 审计。
- **`ErrorCodes`**：异常消息常量 **`EX_MSG_*`** 供 **`GlobalExceptionHandler`** 映射；用户可见响应文案为中文简述。
- **用户列表**：**`GET /api/v1/admin/users`** 的「本租户角色」仅统计成员行 **ACTIVE** 的关系（与成员页一致）。
- **管理端**：成员页增加「含已退出」开关、**邀请成员**、**移出租户**；**所有者/创始人**外**管理员**对成员角色控件只读；用户列表增加说明与「仅显示本租户成员」筛选。

### 0.1.75-SNAPSHOT

- **管理端 `X-Tenant-Id`**：与「工作区切换即换 JWT」对齐，请求头租户**仅取自 JWT `tid`**（不再读 `ai_admin_effective_tenant_id`），避免创始人/多租户场景下头与令牌不一致。

### 0.1.74-SNAPSHOT

- **租户成员与角色**：新增 **`GET /api/v1/admin/tenant-members`**（可选 **`tenantId`**、**`role`**）；**创始人**可按租户查成员，**非创始人**仅能查当前 JWT 租户；与 **`USERS`** 菜单权限对齐（**`AdminHttpMenuRoutes`**）。
- **`PUT /api/v1/admin/users/{id}/tenant-role`**：请求体可选 **`tenantId`**，仅**创始人**可指定以在其它租户下改角色。
- **`TenantMemberRoleApplicationService`**：成员列表、跨租户改角色；**`SysTenantMemberRepository.listByTenantAndRole`**。
- **管理端**：**成员角色**页改为走 **`tenant-members`**，增加**租户**（创始人）、**角色**筛选；**用户管理**本租户角色改为行内 **下拉修改**（与成员页一致）；**`readJwtUid`** 用于禁止改本人角色。

### 0.1.73-SNAPSHOT

- **管理端工作区切换**：仅当存在**多于一条**可切换的「租户 + 管理类角色」时展示切换区；**非创始人**从 **`/admin/me` memberships** 生成 **`租户名称（角色名称）`** 菜单项，点击即切换（确认后换 JWT）。**创始人**：若多条 elevated 成员关系则逐条列出；否则在**租户数 > 1** 时用 **`租户名（创始人）`** 全量列表切换数据租户。移除原弹窗式两步选择。

### 0.1.72-SNAPSHOT

- **管理端用户下拉**：**当前工作区**与摘要改为**两列网格**对齐；**`popper-class`** + 非 scoped 样式加宽下拉层（**min-width 320px**、**max-width** 随视口），避免 teleported 菜单过窄、标签与内容不齐。

### 0.1.71-SNAPSHOT

- **管理端顶栏展示**：**`GET /api/v1/admin/me`** 返回 **`loginName`**（登录名）与 **`displayName`**（昵称，来自 **`sec_user_account.display_name`**）；**`AdminLayout`** 顶栏优先展示昵称，无则回退为 JWT **`sub`**（登录名）。

### 0.1.70-SNAPSHOT

- **MySQL CORS 表索引长度**：**`gw_cors_allowed_origin.origin`** 由 **`VARCHAR(512)`** 改为 **`VARCHAR(191)`**，使 **`UNIQUE KEY uk_gw_cors_origin (origin)`** 在 utf8mb4 下不超过 InnoDB 单索引 **767 字节**（修复 **Error 1071**）。**`db/mysql/schema_v1.sql`** 与 **`migrate_0_1_69_gw_cors_allowed_origin.sql`** 已对齐；已用旧 DDL 建库的库可执行 **`migrate_0_1_70_gw_cors_origin_varchar191.sql`**。
- **网关**：**`CorsAllowedOriginApplicationService`** 校验 Origin 长度 ≤191；管理端 **跨域来源** 表单单行 **`maxlength=191`**。

### 0.1.69-SNAPSHOT

- **管理端工作与角色切换**：右上角租户下拉移除；在**用户菜单**中提供「切换工作与角色」对话框：先选租户（展示**租户名称**），非创始人再选该租户下**管理类角色**（`FOUNDER`/`OWNER`/`ADMIN`）；创始人选任意已存在租户后以创始人身份作用。确认时 **二次确认**，成功后调用 **`POST /api/v1/auth/admin-context`** **重新签发 JWT**（与登录响应同形），并清除仅客户端有效的 **`ai_admin_effective_tenant_id`**，避免与令牌声明漂移。
- **后端**：**`JwtLocalAdminTokenService`**（登录与上下文签发共用）；开放登录 **`memberships`** 每项增加 **`tenantName`**；**`/api/v1/admin/me`** 的成员行增加 **`tenantName`**。错误码 **`ADMIN_CONTEXT_DENIED`**。
- **登录**：管理端登录成功后清除 **`ai_admin_effective_tenant_id`**，默认以新 JWT 的 **`tid`** 为准。

### 0.1.68-SNAPSHOT

- **开放登录错误提示**：新增 **`openAuthHttpErrors`**（管理端 **`web/admin-web`**、用户端 **`web/user-web`** 各一份）：识别 Spring 返回的 **跨域拒绝**（**403** 且响应体含 **`Invalid CORS request`** 等）与 **无响应体的网络错误**（如预检失败），展示**与账号密码无关**的说明；避免误报「账号错误 / 无权限」。**`LoginView`**、**`UserAuthDialog`**（登录与注册）改用上述文案。
- **管理端 `package.json`**：**`npm run dev` / `preview`** 不再写死 **`--port 5174`**，与 **`vite.config.ts`** 中 **`server.port`**（**5176**）一致，避免脚本覆盖配置。

### 0.1.67-SNAPSHOT

- **CORS**：**`WebMvcConfiguration`** 增加 **`http://localhost:5176`**、**`http://127.0.0.1:5176`**（与 **`/open/**`**、**`/api/**`** 白名单一致）。
- **管理端本地 dev**：**`web/admin-web/vite.config.ts`** 默认 **`server.port`** 由 **5174** 改为 **5176**（5174 被占用时可直接 **`npm run dev`**）。

### 0.1.66-SNAPSHOT

- **CORS**：**`WebMvcConfiguration`** 在 **`/open/**`**、**`/api/**`** 上增加 **`http://127.0.0.1:5173`**、**`http://127.0.0.1:5174`**，与既有 **`localhost`** 并列；避免用 **`127.0.0.1`** 打开 Vite 时预检被 Spring 拒绝并仅返回 **`Invalid CORS request`**（**`localhost` 与 `127.0.0.1` 在浏览器里是两个不同 Origin**）。

### 0.1.65-SNAPSHOT

- **MySQL 单一基线**：**`db/mysql/schema_v1.sql`** 合并原分散 **`migrate_*` / `seed_*`**；**每列 `COMMENT`**、**表级 `COMMENT`**；幂等种子与**接口限流清单**并入同文件（清单仅当 **`gw_api_rate_limit_rule` 为空**时批量插入，避免重复执行整文件重复插行）；新增 **`db/mysql/README.md`**。
- **仓库清理**：删除 **`migrate_0_1_*`**、**`migration_047_*`**、**`seed_*`**、**`manual_promote_to_founder.sql`**；一次性运维 SQL 以注释形式置于 **`schema_v1.sql`** 附录。
- **兼容提示**：**`SecUserAccountTableSchema`** 日志改为指引执行 **`schema_v1.sql`**（或等价 **`ALTER`**）补齐 **`jwt_seq`**。
- **规则**：**`.cursorrules` §4.1.3** 更新为以 **`schema_v1.sql`** 为真源、**`migrate_*` 仅可选增量**的脚本体系说明。

### 0.1.64-SNAPSHOT

- **开放登录 403 可诊断**：**`POST /open/v1/auth/login`** 对「账号禁用」与「无 ACTIVE 租户成员」分别返回 **`ApiErrorResponse`** 码 **`LOGIN_ACCOUNT_DISABLED`** / **`LOGIN_NO_ACTIVE_MEMBERSHIP`**（JSON）；后者在服务端逐条 **WARN** 打印 **`sys_tenant_member`** 的 **`status`/`role`**。管理端 **`LoginView`** 按 **`code`** 展示对应中文说明。

### 0.1.63-SNAPSHOT

- **账号表兼容（未跑迁移仍可登录）**：**`SecUserAccount.jwt_seq`** 引入后，若库表**尚未**执行含 **`jwt_seq`** 的 DDL（见当前 **`db/mysql/schema_v1.sql`**），MyBatis 默认 **`SELECT`/`INSERT`** 会引用不存在的列，导致 **`findByLoginName`/`findById`** 等失败（表现常为**登录不可用**，与 **`sys_tenant_member` 数据是否正确无关**）。新增 **`SecUserAccountTableSchema`** 探测列是否存在；缺失时查询排除 **`jwtSeq`**、**`incrementJwtSeq`** 跳过；**`jwtSeq`** 字段 **`insertStrategy`/`updateStrategy = NEVER`**。仍建议执行 **`schema_v1.sql`** 中相关 **`ALTER`/建表**并**重启**以启用完整 JWT 代际踢下线。

### 0.1.62-SNAPSHOT

- **JWT 踢下线**：**`sec_user_account.jwt_seq`**；登录 JWT 增加 **`jseq`**；**`JwtSessionGateFilter`**；**`POST /api/v1/admin/users/{id}/kick-session`**；**`POST /api/v1/admin/users/{id}/ban`**（账号与当前租户成员置禁用并使令牌立即失效）。
- **菜单管理**：枚举 **`MENU_CATALOG`**；表 **`sys_admin_menu_item`**；**`/api/v1/admin/menu-items`** CRUD；管理端 **`/system/menu-items`**；租户「后台开放菜单」勾选并入 **`TenantsView`** 操作抽屉（原独立「租户菜单」页移除）。
- **接口与限流**：枚举 **`GATEWAY_API`**；表 **`gw_api_rate_limit_rule`**；**`/api/v1/admin/gateway-rate-limits`** CRUD；**`ApiRateLimitFilter`**（按 Ant 路径 + 方法 + 租户/开放维度固定窗口）；接口清单种子已并入 **`db/mysql/schema_v1.sql`**（默认 **enabled=0**，见 **0.1.65** 合并说明）。
- **迁移与种子（已由 0.1.65 合并入 `schema_v1.sql`）**：原 **`migrate_0_1_62_*`**、**`seed_*`** 等文件已删除；新环境仅维护 **`schema_v1.sql`**。
- **管理端登录 403**：**`AuthLoginController`** 在账号禁用或无**活跃**租户成员时打 **WARN** 日志（便于核对 **`sys_tenant_member.status`**、**`user_id`**）。**`DefaultAdminAccountBootstrap`** 在已存在 **`admin`** 账号时仍会启动自检：无可用 elevated 成员则向 **`com.aaron.cloud.tenant.default-id`** 租户补 **FOUNDER** 行或**激活/升权**（纯 **MEMBER** 升为 **OWNER**，否则 **`AdminMemberDenyFilter`** 会拒绝 **`/api/v1/admin/*`**）。
- **修订说明**：**`.cursorrules` §4.1.3** 以 **`schema_v1.sql`** 为权威全量、**可选 `migrate_*`** 仅作已上线库单次增量；表前缀清单增加 **`gw_*`**，并明确网关域独占表**禁止**占用 **`sys_`**。

### 0.1.61-SNAPSHOT

- **用户端对话**：开启「思考」时，**仅先展示思考区**；**主回复气泡**在收到**首个非空正文 token** 后再出现，并**自动收起**思考正文（顶栏「思考过程」仍可点开查看）。

### 0.1.60-SNAPSHOT

- **开放上下文**：**`GET /open/v1/system/me`** 在存在 **`tenantId`** 时附带 **`tenantName`**、**`tenantCode`**（查 **`sys_tenant`**）；用户端 **`MeView`** 主文案展示为 **租户名称（租户代码）**，下方保留 **租户 ID** 辅助说明。

### 0.1.59-SNAPSHOT

- **租户运行时系统配置**：表 **`ten_runtime_setting`**（DDL 见当前 **`db/mysql/schema_v1.sql`**）；枚举 **`TenantRuntimeSettingKey`**（首项 **`AUTH_OPEN_REGISTRATION`**）；**`TenantRuntimeSettingApplicationService`** + **Redis** 读穿缓存（**`ai:cfg:tenant:{tenantId}:{key}`**，TTL **`com.aaron.cloud.tenant-runtime-settings.cache-ttl-seconds`**）；**`POST /open/v1/auth/register`** 按**注册目标租户**读取开关。
- **管理端**：菜单码 **`SYSTEM_SETTINGS`**；**`GET/PUT /api/v1/admin/tenant-runtime-settings`**；侧栏 **系统 → 系统参数**（**`TenantRuntimeSettingsView`**）；种子脚本补充 **`SYSTEM_SETTINGS`**。
- **配置分层**：**`application.yml`** 增加 **`com.aaron.cloud`** 段注释与 **`com.aaron.cloud.tenant-runtime-settings`**；移除 **`com.aaron.cloud.auth.open-registration-enabled`**（改走库表 + 管理端）。

### 0.1.58-SNAPSHOT

- **配置**：**`application.yml`** 下 **`com.aaron.cloud`** 合并重复的 **`auth`** 键（**`open-registration-enabled`** 与 **`jwt-local`** 同属 **`com.aaron.cloud.auth`**），修复 SnakeYAML **`DuplicateKeyException`** 导致应用无法启动。
- **用户端响应式**：**`useWindowBreakpoints`**（&lt;720 手机 / 720–1023 平板 / ≥1024 桌面）；**`ChatView`** 手机侧栏抽屉与顶栏、**`100dvh`** 与 **safe-area**、触控尺寸与模型选择宽度上限；**`index.html`** `viewport-fit=cover`；**`MeView`** 小屏与刘海区适配。

### 0.1.57-SNAPSHOT

- **管理端**：**`lnk_*_admin_menu.menu_code`** 仍为英文码；**`adminMenuLabelZh`** 统一中文展示；**`TenantMenusView`** 合并库中多出的码并修正默认「全选」逻辑，说明文案标明「库码 / 界面中文」。

### 0.1.56-SNAPSHOT

- **开放认证**：**`POST /open/v1/auth/register`**（可配置 **`com.aaron.cloud.auth.open-registration-enabled`**）；**`/open/v1/auth/login`** 支持**仅 MEMBER** 的 C 端账号（原仅允许创始人/OWNER/ADMIN 登录）。
- **用户端**：**`http` / SSE** 自动携带 **`Authorization: Bearer`**；**`UserAuthDialog`** 弹窗登录/注册（无整页跳转）；**`ChatHistoryMessage`** 与气泡展示 **`modelAlias`**；登录后可写 **`ai_user_effective_tenant_id`** 以与 **`X-Tenant-Id`** 对齐。
- **管理端对话日志**：**`ChatMessageView`** 增加 **`modelAlias`**；**`/chat/conversations`** 页抽屉内助手消息 **Markdown**、与用户端一致的 **token 文案**及模型别名；侧栏菜单名 **对话日志**（依赖 **markdown-it**、**dompurify**）。
- **网关**：**`TenantContextFilter`** 对 **MEMBER** 仅在访问 **`/api/v1/admin/*`** 时拒绝 **`X-Tenant-Id`** 切换；**开放对话等 C 端路径**允许 MEMBER 携带与租户一致的 **`X-Tenant-Id`**，避免登录后 403。

### 0.1.55-SNAPSHOT

- **LLM 用量落库**：每轮对话在流式**正常结束后**对 **`llm_model.tokens_used`** 与 **`metering_usage_event`（`llm.chat.completion`）** 做**同步落库**（Redis 额度 Lua 成功后）；异步队列仅作失败重试，避免仅依赖 Redis List / RocketMQ 时管理端模型用量长期为 **0** 而用户端消息 meta 已有用量。
- **OpenAI usage 解析**：**`OpenAiUsageParser`** 兼容 **`camelCase`** 的 **`promptTokens` / `completionTokens` / `totalTokens`**。
- **管理端**：新增菜单码 **`CHAT`**；**`GET /api/v1/admin/chat/conversations`**、**`GET /api/v1/admin/chat/conversations/{id}/messages`**（**`ChatAdminRestController`**）；侧栏 **对话抽检**（**`/chat/conversations`**）；菜单种子补充 **`CHAT`**（已并入 **`db/mysql/schema_v1.sql`**，见 **0.1.65**）。

### 0.1.54-SNAPSHOT

- **管理端**：侧栏增加 **租户菜单**（创始人，`/tenant/menus`）、**用户管理**（`USERS`，`/users`，含成员与账号能力）；**`TenantMenusView`**；**`PUT /api/v1/admin/users/{id}/tenant-role`** 与 **`TenantMemberRoleApplicationService`**。
- **数据库**：原 **`seed_admin_menu_full_bootstrap.sql`** 行为（一键写入 **`lnk_tenant_admin_menu`** 及 **`admin`** 的 **`lnk_tenant_user_admin_menu`**）已并入 **`db/mysql/schema_v1.sql`**（见 **0.1.65**）。

### 0.1.53-SNAPSHOT

- **对话会话标题**：首条用户消息写入且会话标题仍为默认占位（**`新会话`** 或以 **`新对话`** 开头）时，自动将标题更新为**首条问题首行**（过长截断）；**`ChatConversationRepository.updateTitle`**。
- **用户端**：**`ChatView`** 在每次发送 **`finally`** 中 **`refresh`** 会话列表，侧栏标题与排序与后端一致。

### 0.1.52-SNAPSHOT

- **管理端菜单**：代码中明确 **FOUNDER** 始终拥有 **`AdminMenuCode`** 全量权限（与 **`lnk_tenant_admin_menu`** 无关）；原 **`seed_lnk_tenant_admin_menu_all_codes.sql`**（每租户 **INSERT IGNORE** 全量菜单码）已并入 **`db/mysql/schema_v1.sql`**（见 **0.1.65**）。

### 0.1.51-SNAPSHOT

- **管理端菜单授权**：**`ADMIN`** 在 **`lnk_tenant_user_admin_menu`** 尚无个人配置时，**继承租户可用菜单**（与租户级菜单表为空时「视为全开」一致），避免访问 **`/api/v1/admin/access-logs`** 等接口被 **`AdminMenuAuthorizationFilter`** 误判 **403**。

### 0.1.50-SNAPSHOT

- **编译**：修复 **`TenantContextFilter`** 中 lambda 对 **`memberRole`** 赋值导致的「变量应为 final」编译错误。
- **修订说明**：**`jwtTenantId`** 先置 **`null`** 再在 JWT 分支赋值，对 lambda 而言非 effectively final；改为使用 **`final long tidForTmsMatch`** 参与 **`stream().filter(...)`**，消除「从 lambda 引用本地变量须为 final」报错。
- **Maven**：增加 **`maven-enforcer-plugin`**，在 **`validate`** 阶段要求 **JDK ≥ 25**、**Maven ≥ 3.6.3**；若本机默认 **`mvn`** 仍绑定 **Java 8**，将直接失败并提示设置 **`JAVA_HOME`** 或使用仓库 **`mvnw.cmd`**。

### 0.1.49-SNAPSHOT

- **对话流式落库**：**`ChatApplicationService`** 在虚拟线程中调用 **`linkMessage`** 时不再依赖 **`TenantContextHolder`**（请求线程 Filter 已清理上下文），改为显式传入 **`tenantId`**，修复流式正常结束后 **`IllegalStateException: tenant context missing`**。

### 0.1.48-SNAPSHOT

- **管理端顶栏**：用户菜单触发器改为**无边框、透明底**的轻量样式，与「当前租户」切换同排展示，弱化按钮感。
- **用户端对话 Markdown**：**`markdown-it`** 关闭 **`breaks`**（与常见 CommonMark 预览一致），并对助手正文做轻量换行归一；**`.bubble-md`** 为松散列表内 **`li > p`** 去掉多余外边距，缓解「`1.` 与 `**标题**` 纵向错开多行」的版式问题。

### 0.1.47-SNAPSHOT

- **管理端多租户与安全**：JWT 增加 **`tms`**（用户在各租户的 `tid`+`tmr`）；**`X-Tenant-Id`** 对非创始人须命中 **`tms`** 且角色非 **MEMBER**，否则 **403**；创始人切换时校验租户存在。**`TenantContextFilter`** 按上述规则解析上下文。
- **纯成员禁入控制台**：**`AdminMemberDenyFilter`** 拦截 **`/api/v1/admin/*`**（放行 **`GET /api/v1/admin/me`**）；登录时若所有活跃成员关系均为 **MEMBER** 则 **403**。
- **菜单与 API 授权**：**`AdminMenuAuthorizationFilter`** + **`AdminHttpMenuRoutes`**（含 access-logs、audit-events、metering-events、file-objects 等）；**`GET/PUT /api/v1/admin/tenants/{id}/admin-menus`**（创始人）、**`GET/PUT /api/v1/admin/users/{id}/admin-menus`**（所有者/创始人，目标须为 **ADMIN**）；**`GET /api/v1/admin/me`** 返回 **`allowedMenuCodes`** 等。
- **DDL**：**`lnk_tenant_admin_menu`**、**`lnk_tenant_user_admin_menu`** 写入 **`schema_v1.sql`**。
- **管理端前端**：**`readJwtTms`**、请求头租户解析与 **`AI_ADMIN_MEMBERSHIPS_KEY`**；顶栏 **当前租户** 下拉支持多租户非创始人；侧栏菜单按 **`/admin/me`** 的 **`allowedMenuCodes`** 裁剪。

### 0.1.46-SNAPSHOT

- **租户成员角色**：新增 **`TenantMemberRole.FOUNDER`（创始人）**；JWT 增加 **`tmr`** 声明；**`TenantContextHolder`** 携带 **`memberRole`**。创始人可通过 **`X-Tenant-Id`** 切换数据租户（**`TenantContextFilter`**）；非创始人强制 **`tid`**，防伪造头跨租户。
- **登录**：**`AuthLoginController`** 按用户全部成员关系解析是否为创始人并写入 **`tmr`**；**`DefaultAdminAccountBootstrap`** 新建默认 **`admin`** 成员角色改为 **FOUNDER**。
- **租户管理 API（仅创始人）**：**`GET/POST/PUT /api/v1/admin/tenants`**（**`AdminTenantRestController`** + **`TenantAdminApplicationService`**），**`SysTenantRepository.listAllOrderById` / `updateById`**，**`SysTenantMemberRepository.listByUserId`**。
- **用户管理**：仅创始人可为新用户分配 **FOUNDER**（**`AdminUserRestController`**）。
- **管理端**：**`jwtSubject`** 解析 **`tmr`/`tid`**；**`http`** 在 **`tmr=FOUNDER`** 时使用 **`ai_admin_effective_tenant_id`** 作为 **`X-Tenant-Id`**；侧栏 **「平台 → 租户管理」**、顶栏 **数据租户** 下拉；**`TenantsView`**；路由 **`/tenant/tenants`** 仅创始人可进。
- **存量库**：将已有 **admin** 成员升为 **FOUNDER** 的运维 **SQL** 以注释形式写在 **`db/mysql/schema_v1.sql`** 附录（原独立 **`manual_promote_to_founder.sql`** 已删除，见 **0.1.65**）。

### 0.1.45-SNAPSHOT

- **管理端（`web/admin-web`）**：顶栏右侧改为 **用户名 + 下拉**（JWT `sub` 解析，免登录预览模式显示「免登录预览」），菜单内 **重新登录**（清 Token 并带 redirect 回登录页）、**退出**。**用户管理**：去掉常驻新建表单，改为 **「新增用户」打开对话框**；列表 **角色**、**状态** 中文展示。
- **开放管理用户 API**：**`UserView`** 含 **`loginName`**/**`tenantRole`** 等（当前租户成员角色；列表按租户成员表聚合，未加入租户为 null）。

### 0.1.44-SNAPSHOT

- **开放对话与会话隔离**：**`GET /open/v1/chat/conversations/{id}/messages`** 返回本会话有序消息（**`role` / `content` / `reasoning` / `promptTokens` / `completionTokens` / `totalTokens` / `createdAt`**）；**`listConversations`** 改为 **已登录仅 `user_id` 本人**、**访客仅 `user_id IS NULL` 且 `device_id` 与请求头一致**；**流式发送**前校验会话归属，防跨用户/跨设备读会话。**`linkMessage`** 后 **`touchUpdatedAt`** 会话，侧栏排序与最近活动一致。**SSE `end`** 帧在可用时附带 **`usage`**（与落库 `meta_json` 一致）。
- **用户端（`web/user-web`）**：**`getOrCreateDeviceId`** 导出并与 **SSE fetch** 共用；**切换会话拉历史**、**顶栏本会话 token 累计**、**单条助手 token 脚注**；流式 **`end`** 解析 **`usage`** 并写入当前气泡。

### 0.1.43-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：助手正文使用 **`markdown-it`** + **`dompurify`**（`renderMarkdownToSafeHtml`）**安全渲染 Markdown**；思考区在 **流式结束后默认折叠**，点击标题条可 **展开/收起**（流式中始终展开且不可点收）。

### 0.1.42-SNAPSHOT

- **用户端（`web/user-web` `ChatView`）**：模型下拉里层 **`min-width`** 不再固定为 **`min(90vw,360px)`**；新增 **`measureDropdownOptionTextWidthPx`**，对 **「选择模型」占位 + 各选项 `modelOptionLabel`** 取最长文案宽度，加 **`44px`** 余量（内边距、勾选、滚动条），并与 **触发器已算宽度** 取 **`max`**，再按视口 **`92vw`** 封顶。通过 **`watch` + `documentElement` 的 `--chat-model-dd-min`** 配合全局样式 **`!important`** 覆盖组件内联 **`min-width`**；离开页 **`onBeforeUnmount`** 移除变量。修正 **`import * as chatApi`** 位置。

### 0.1.41-SNAPSHOT

- **用户端（`web/user-web` `ChatView`）**：模型 **`el-select`** 去掉 **`fit-input-width`**（与窄触发器同宽会裁切选项）。为下拉增加 **`popper-class="model-select-dropdown"`**，全局样式对 **`.el-select-dropdown.model-select-dropdown`** 设置 **`min-width: min(90vw, 360px) !important`**（覆盖组件内联 `min-width`）、**`max-width`**；选项 **`white-space: normal`**、增高 padding，长文案可多行展示。

### 0.1.40-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：主区与列表、输入区统一为 **`#fafafa` 底 + 中间白卡片`composer-surface`**，去掉底栏与消息区「白叠灰」的割裂顶线；气泡恢复为**灰底圆角**（接近早期风格），头像小方角+绿/深灰。去掉有消息时输入框上方的快捷条。模型 **`el-select`** 使用 **`measureSelectLabelWidthPx`** 按当前展示名动态 **`width`**，并 **`fit-input-width`**；触发器**默认透明无边**，悬停浅灰底、聚焦淡灰边。发送恢复**圆形图标**。
- **用户端「设备与登录」页（`MeView`）**：不再展示整段 JSON；改为**卡片式**说明租户、登录状态、设备码及用途，并提供**复制设备码**（`navigator.clipboard`）。侧栏入口文案改为「设备与登录」。

### 0.1.39-SNAPSHOT

- **用户端对话页（`web/user-web` `ChatView` + `global.css`）**：对齐千问式视觉——主色 **`#409EFF`**、内容区背景 **`#F5F7FA`**、顶栏白底分隔；**助手气泡**白底浅描边+左上小圆角、**用户气泡**主色底白字+右上小圆角；**32px 圆形头像**（`User` / `ChatLineRound`）。**输入卡片**圆角 16px、**聚焦**主色描边+浅外发光；**模型下拉**默认透明无边框、**悬停 / 聚焦**才显背景与描边；**深度思考**为幽灵条+开关；**发送**改为圆角矩形主按钮文案「发送」。**快捷问题**（空状态三条 + 有消息时输入区上方紧凑条）、**清空**输入、占位与行数（默认 3 行最大 6 行）及 **Shift+Enter** 提示。

### 0.1.38-SNAPSHOT

- **开放「当前上下文」**：**`GET /open/v1/system/me`** 原用 **`Map.of`** 组装 **`tenantId` / `userId` / `deviceId`**；访客 **`userId`** 为 **`null`** 时 **`Map.of` 抛 NPE**。改为 **`LinkedHashMap`** 显式 **`put`**，无上下文分支同样不再使用含 null 的 **`Map.of`**。

### 0.1.37-SNAPSHOT

- **用户端对话（`web/user-web` `ChatView`）**：去掉 **mock 本地占位** 在模型列表中的展示；默认选中首个**未用尽额度**的租户模型；无可用模型或列表加载失败时 **`ElMessage`** 提示。输入区参考 **豆包式** 整合布局：**大圆角卡片**（`composer-surface`）内 **上方多行输入**（占位「发消息…」）、**下方工具条**：圆形 **「+」** 添加附件、竖分割线、**胶囊形模型下拉**、可选 **「思考」** 开关、右侧 **圆形发送**。
- **开放模型列表 API**：**`ChatApplicationService.listModelsForChatPicker`** 不再注入 **`mock`** 项（编排层仍接受显式 **`mock`** 别名以兼容旧客户端/测试；引擎 **`DefaultModelCompletionEngine`** 未改）。

### 0.1.36-SNAPSHOT

- **用户端对话页（`web/user-web` `ChatView`）**：将 **模型选择** 与 **思考过程** 开关从主区右上角移至 **输入框上方** 工具条（与输入卡片同宽居中），标题栏仅保留会话标题与附件提示，避免控件挤在顶栏一侧。

### 0.1.35-SNAPSHOT

- **错误与排障日志**：**`GlobalExceptionHandler`** 在各类异常日志中附带 **`RequestLogSupport.currentRequestLine()`**（方法 + URI，query 截断）；**`MethodArgumentNotValidException`** 输出字段级 **`field:message`** 摘要（最多 16 条）。**`OpenAiChatStreamClient`** 对非 2xx、HTTP 发送 IO、SSE 行 JSON 解析失败打 **`ERROR`**，含 **`OpenAiCallContext`**（租户、别名、`llm_model.id`、厂商 `model`、配置基址）、**`resolveChatCompletionsUrl` 结果**、以及 **`summarizeChatJsonBody`**（上游 model/stream/消息条数/角色序列/JSON 长度，**不含**各条 message 的 content）。**`DefaultModelCompletionEngine`** 传入上述上下文。**`ModelApplicationService.streamCompletion`** 失败时记录租户/用户/设备/别名/思考开关及 **`summarizeModelRequest`**。**`ChatApplicationService`** 流式失败与 SSE 写失败日志补充会话、模型行、意图、轮次与用户文本长度（仍不落用户原文）。新增 **`RequestLogSupport`**、**`OpenAiCallContext`**；**`OpenAiChatStreamClientUrlTest`** 覆盖 **`summarizeChatJsonBody`** 不泄露 content。

### 0.1.34-SNAPSHOT

- **对话 SSE 失败路径**：流式线程内模型调用异常时不再 **`SseEmitter.completeWithError`**（易在 **`Accept: text/event-stream`** 下触发 **`GlobalExceptionHandler`** 写 JSON，继而 **`HttpMediaTypeNotAcceptableException`**）。改为向客户端发送 **`content`** 分帧（含简要错误说明）+ **`end`** 后 **`complete()`**。

### 0.1.33-SNAPSHOT

- **OpenAI 兼容 URL**：**`OpenAiChatStreamClient`** 原先对任意 `openai_base_url` 追加 **`/v1/chat/completions`**；若基址已为 **`…/v1`**（如阿里云 DashScope **`https://dashscope.aliyuncs.com/compatible-mode/v1`**），会误拼成 **`…/v1/v1/chat/completions`** 导致 **HTTP 404**。现 **`resolveChatCompletionsUrl`**：已以 **`/v1`** 结尾则只补 **`/chat/completions`**；已以 **`/chat/completions`** 结尾则不再拼接；否则补全 **`/v1/chat/completions`**。上游非 2xx 时在异常信息中带 **`url=`** 与截断 **`body=`** 便于排障。**`OpenAiChatStreamClientUrlTest`** 覆盖上述分支。

### 0.1.32-SNAPSHOT

- **用户端（`web/user-web`）**：对话页视觉与 **ChatGPT** 对齐——白底主区、浅灰侧栏、气泡与头像简化色；**输入区** 单卡片圆角容器、**回形针** 触发上传、**胶囊附件条**（文档图标 + 截断文件名 + 圆形移除）、**拖拽** 高亮、计数 **n/max**；发送钮改为 **深圆角条**；**`global.css`** 背景改为纯白。

### 0.1.31-SNAPSHOT

- **管理端布局**：**`global.css`** 为 **`admin-shell`** 固定视高、**`el-main`** 与路由根节点 **flex 纵向铺满**；**`.page > .el-card.panel`** 与 **用户页列表卡片** 的 **card body** 可伸缩、表格区可滚动；去掉各业务页 **`max-width`**（AI 中台、用户、观测页统一横向占满 **`main`** 内容区）。

### 0.1.30-SNAPSHOT

- **管理端（`web/admin-web`）**：侧栏 **「AI 中台」** 置顶，**「用户与组织」** 其次，**「观测与审计」** 移至最下；**审计事件**、**计量事件** 页改为与 **访问日志** 一致的 **表格 + 分页 + 行详情弹窗**（`AuditEventsView` / `MeteringView`）；**`api/admin.ts`**、**`types/admin.ts`** 补充分页 DTO 类型。

### 0.1.29-SNAPSHOT

- **REST 映射可靠性**：Spring 对类型链上**多个**类级 **`@RequestMapping`** 的合并不可靠，部分接口实际只挂在 **`/admin/...`** 等路径，访问 **`/api/v1/...`** 会 **404**。现 **`AbstractApiV1Controller` / `AbstractOpenV1Controller`** 仅保留 **`PREFIX`** 常量；**`ApiV1ControllerBases` / `OpenV1ControllerBases`** 使用 **`PREFIX + 资源段`** 声明**单条**完整类级路径。
- **前端开发代理**：**`web/admin-web`**、**`web/user-web`** 的 **`vite.config.ts`** 增加 **`/open` → 后端**，与 **`/api`** 一致，避免未设置 **`VITE_API_BASE`** 时 **`/open/v1/...`** 落到 Vite 无映射。

### 0.1.28-SNAPSHOT

- **REST 路径组织**：恢复 **`AbstractApiV1Controller`**（**`/api/v1`**）、**`AbstractOpenV1Controller`**（**`/open/v1`**）上的类级前缀；新增 **`ApiV1ControllerBases`** / **`OpenV1ControllerBases`** 中的**嵌套抽象类**承载各资源段（如 **`/admin/users`**、**`/chat`**）。各域 **`@RestController`** 仅继承对应嵌套类型，**不再**在具体类上声明 **`@RequestMapping`**，避免子类覆盖父前缀；对外 URL 与 **0.1.27** 一致。
- **测试**：**`AdminUserRestControllerWebMvcTest`**（**`@WebMvcTest`** + **`TenantContextHolder`**）断言 **`GET /api/v1/admin/users`** 可命中控制器，防止路径继承链被误改。

### 0.1.27-SNAPSHOT

- **REST 路径修复**：Spring MVC 中子类类级 **`@RequestMapping` 会覆盖父类**，原先在 **`AbstractApiV1Controller` / `AbstractOpenV1Controller`** 上声明的 **`/api/v1`、`/open/v1`** 实际未生效，导致 **`/api/v1/admin/users`**、**`/api/v1/admin/access-logs`**、**`/open/v1/auth/login`** 等返回 **404**。现各具体控制器使用**完整路径**（如 **`/api/v1/admin/users`**、**`/open/v1/chat`**）；抽象基类仅作标记、不再声明路径。
- **前端**：管理端 **`global.css`**（主内容区浅底、顶栏层次）；用户端 **`global.css`** + **`ChatView`**（侧栏层次、新对话按钮、空状态与主区背景微调）。

### 0.1.26-SNAPSHOT

- **Redis**：引入 **`spring-boot-starter-data-redis`**；**`spring.data.redis.*`** + **`com.aaron.cloud.redis.enabled`**（与空 host 时自动装配跳过配合）；**`LlmTokenQuotaRedisOps`** 用 **Lua** 对 **`ai:llm:tokens:{tenant}:{model}`** 原子累加并与上限比较，高 QPS 下避免直打 MySQL 行更新。
- **用量异步落库**：**`LlmUsageAsyncPublisher`** — 优先 **`RocketMQ`**（**`rocketmq.app.llm-usage-topic`**）；失败或未启用 MQ 时 **`RPUSH`** 至 Redis List（**`com.aaron.cloud.llm-usage.redis-queue-key`**）；无 Redis 则 **同步** **`LlmUsagePersistenceService.persist`**。**`LlmUsageRocketMqListener`** / **`LlmUsageRedisQueuePoller`**（**`@Scheduled`**，`rocketmq.app.enabled=false` 时启用轮询）共用持久化逻辑（**`appendTokensUsed`** + **`metering_usage_event`**）。
- **编排**：**`LlmTokenQuotaCoordinator`**；**`ChatApplicationService`** 流后仅 Redis 扣量 + 投递 digest；**`LlmModelAdminApplicationService`** 变更后刷新/失效 Redis 计数键。
- **其它**：**`@EnableScheduling`** 置于 **`AiApplication`**。

### 0.1.25-SNAPSHOT

- **流式 usage（豆包 / OpenAI 兼容）**：请求体增加 **`"stream_options": { "include_usage": true }`**；**`OpenAiChatStreamClient`** 解析 SSE 根级 **`usage`**（含最后一帧仅有 usage、无 choices 的情况），经 **`ModelChatRequest.streamUsageConsumer`** 上送 **`ModelTokenUsage`**。
- **模型共用 token 额度**：表 **`llm_model`** 增加 **`token_quota_total`**（NULL=不限制）、**`tokens_used`**（累计消耗）；预检 **`tokens_used >= token_quota_total`** 时拒绝对话并提示 **「该模型没额度了」**；流式结束后 **`addTokensUsedWithinQuota`** 原子累加；**`metering_usage_event`** 写入 **`llm.chat.completion`** 计量（含 prompt/completion/total、conversationId、llmModelId）。
- **管理端**：可配置 **token 上限**（新建/编辑）；列表展示已用量与上限；列 **`token_quota_total` / `tokens_used`** 的 DDL 见 **`db/mysql/schema_v1.sql`**（原 **`migrate_0_1_25_*`** 已并入，见 **0.1.65**）。
- **用户端**：模型选择器对 **`quotaExhausted`** 项 **禁用** 并标注额度用尽。

### 0.1.24-SNAPSHOT

- **库表命名规范**：**`.cursorrules` §4.1.3** 增补 **MySQL 物理表名与索引命名**（域前缀、`lnk_*`、禁止滥用 `sys_` 等）；**`llm_model`** 为租户 LLM 配置规范表名（原 **`sys_llm_model`**）；存量库若仍用旧表名，**`RENAME TABLE`** 示例见 **`db/mysql/schema_v1.sql`** 附录注释（原 **`migrate_0_1_24_*`** 已并入，见 **0.1.65**）。
- **管理端数据驱动**：**MCP** — **`GET/POST/PUT/DELETE /api/v1/admin/mcp-servers`**（**`McpServerAdminRestController`** + **`McpServerAdminApplicationService`**），从 **`gateway.AdminReadController`** 移除只读 MCP 列表以免路由重复；**`web/admin-web`** **`McpServersView`** 全量 CRUD，**`api/mcpAdmin.ts`**。
- **RAG 知识库管理**：**`GET/POST /api/v1/admin/rag-kbs/{tenantCode}`**、**`PUT/DELETE .../{tenantCode}/{kbId}`**、**`POST .../{tenantCode}/{kbId}/index-jobs`**（**`RagKbAdminRestController`** + **`RagKbAdminApplicationService`**）；删除前校验 **`lnk_rag_kb_document`** 无关联；**`LnkRagKbDocumentRepository`**；**`RagKnowledgeBasesView`** + **`api/ragAdmin.ts`**。
- **异常**：**`GlobalExceptionHandler`** 增加 **`ResponseStatusException`** 映射（404/409/400 等与 **`ErrorCodes`** 对齐）。

### 0.1.23-SNAPSHOT

- **可配置 LLM（OpenAI 兼容）**：新增表 **`llm_model`**（原脚本中曾用名 **`sys_llm_model`**；租户、别名、Base URL、Model ID、**API Key AES-GCM 密文**、**访客是否可用**、**单轮附件上限 0–10**、**是否支持思考流**、启用/排序等）；**`AesSecretCipher`**（配置 **`com.aaron.cloud.llm-model.api-key-encryption-secret`**，否则回退 **`com.aaron.cloud.auth.jwt-local.secret`** 须 ≥32 字节）。
- **统一推理引擎**：**`DefaultModelCompletionEngine`** 替代原 **`LocalMockModelEngine` / `OpenAiCompatibleModelEngine`**；**`mock`** 仍为内置占位；租户配置模型走 **SSE `/v1/chat/completions`**。（**0.1.155** 起已删除 yaml 全局 OpenAI 对话回退，须配置租户 **`llm_model`**。）
- **对话**：**`POST /open/v1/chat/conversations/{id}/messages`** 请求体改为 **`ChatSendPayload`**（**`content` / `modelAlias` / `thinkingEnabled` / `attachmentIds`**）；SSE **`data`** 为 JSON 分帧 **`content` / `reasoning` / `end`**；用户/助手消息 **`meta_json`** 记录模型与思考开关及附件 id；**`GET /open/v1/chat/models`** 供 C 端选择器（访客仅 **`allow_anonymous=ALLOWED`** 的模型 + **`mock`**）。
- **附件**：表 **`chat_attachment`**；**`POST .../attachments`** multipart **`files[]`**，**Apache Tika** 抽取 PDF/Office/图片容器等文本并入库；单文件 **≤15MB**，**`spring.servlet.multipart`** 上限已放宽。
- **管理端**：**`/model/llm-models`** 页 CRUD 调用 **`/api/v1/admin/llm-models`**；**`web/admin-web/src/api/models.ts`**。
- **用户端**：对话顶栏 **模型选择**、**思考开关**（仅模型支持时显示）、**附件选择**与发送前上传。
- **依赖**：**`tika-core` + `tika-parsers-standard-package`**。

### 0.1.22-SNAPSHOT

- **管理端 UI（`web/admin-web`）**：侧栏改为 **靛蓝统一色系** 与 **分组子菜单**（观测与审计 / AI 中台 / 用户与组织），替换原先混杂的青绿高亮。**访问日志**改为 **表格 + 分页**，点击行或「详情」打开 **弹窗**（结构化字段 + 可复制 JSON），不再以整页 JSON 为主视图。
- **AI 中台菜单**：新增 **模型管理**（`/model/routing` 骨架）、**MCP 管理**（`/mcp/servers` 联调 **`GET /api/v1/admin/mcp-servers`** 分页表）、**RAG 知识库**（`/rag/knowledge-bases` 联调 **`GET /api/v1/rag/kbs`**）；补充 **`src/types/admin.ts`** 与 **`api/admin.ts`** 封装。
- **规则**：**`.cursorrules` §3.5** 增加管理端观测页与 **model/mcp/rag** 菜单入口约定。

### 0.1.21-SNAPSHOT

- **REST 工程化**：**`com.aaron.cloud.common.web.rest`** 新增 **`AbstractOpenV1Controller`**（**`/open/v1`**）、**`AbstractApiV1Controller`**（**`/api/v1`**）；各域对外控制器迁入 **`rest.open`** / **`rest.api`** 子包并继承对应基类，子类 **`@RequestMapping`** 仅写资源段，URL 与 **§6.0** 一致。**`gateway.admin.AdminReadController`** → **`gateway.rest.api`**；**`identity.admin.AdminUserRestController`** → **`identity.rest.api`**；**`chat`/`identity`/`file`/`notification`/`mcp`/`rag`** 的 REST 类按 **`open`/`api`** 归类。
- **规则**：**`.cursorrules` §6.0、§3.4、§9** 补充 **`rest.open`/`rest.api`** 分包与抽象前缀基类约定。

### 0.1.20-SNAPSHOT

- **HTTP 路径与鉴权**：约定 **`/open/**`** 免 JWT、**`/api/**`** 须认证（见 **`.cursorrules` §6.0**）。**`ChatConversationController`**、**`SystemController`**、**`AuthLoginController`** 的对外前缀改为 **`/open/v1/...`**；**`SecurityJwtLocalConfiguration`** / **`SecurityOAuth2ResourceConfiguration`** 对 **`/open/**`** **`permitAll`**；**`WebMvcConfiguration`** 为 **`/open/**`** 增加与 **`/api/**`** 一致的 CORS。
- **前端**：**`web/user-web`** 对话与 **`/system/me`** 改调 **`/open/v1/...`**；**`web/admin-web`** 登录改 **`POST /open/v1/auth/login`**。
- **`application.yml`** 注释与 **`PROJECT.md`** 同步上述登录路径。

### 0.1.19-SNAPSHOT

- **jwt-local 登录**：**`AuthLoginController`** 签发访问令牌时显式使用 **`JwsHeader.with(MacAlgorithm.HS256)`** 与 **`JwtEncoderParameters.from(jws, claims)`**。此前仅 **`JwtEncoderParameters.from(claims)`** 时默认 JWS 算法为 **RS256**，与 **`JwtLocalSigningConfiguration`** 中仅注册的 **HS256** 对称 **`OctetSequenceKey`** 不一致，**`NimbusJwtEncoder`** 选不到签名密钥并报 **`JwtEncodingException: Failed to select a JWK signing key`**。

### 0.1.18-SNAPSHOT

- **鉴权默认**：**`com.aaron.cloud.providers.auth`** 默认由 **`permit`** 改为 **`jwt-local`**，确保 **`AuthLoginController`** 注册本地登录接口（**0.1.20** 起为 **`POST /open/v1/auth/login`**，与 **`SecurityJwtLocalConfiguration`** 白名单一致）；管理端登录不再落到静态资源 **`NoResourceFoundException`**。本地若仍要全开接口可设 **`AI_PROVIDERS_AUTH=permit`** 并配合管理端 **`VITE_ADMIN_AUTH_SKIP=true`**。
- **异常**：**`GlobalExceptionHandler`** 增加 **`NoResourceFoundException`** → **404** + **`NOT_FOUND`**，便于区分「无映射」与 **500**。
- **`SecurityPermitAllConfiguration`**：去掉 **`matchIfMissing=true`**，仅在显式 **`permit`** 时启用。
- **修订说明**：**`.cursorrules`** 新增 **§7.1 用户可见文案（产品导向）**、**§8** 补充禁止开发者向主文案；**`web/admin-web` 登录页**信息提示与失败文案改为面向运营/管理员表述，按 **401/403/404/5xx/网络** 分类；技术细节走 **`console.warn`**。

### 0.1.17-SNAPSHOT

- **管理端**：**路由守卫**——无 **`ai_admin_access_token`** 时一律跳转 **`/login`**，并带 **`redirect`**；已登录访问登录页则进入业务页；**`axios` 401** 时清 Token 并动态 **`import`** 路由后跳转登录，避免循环依赖；**`AdminLayout`** 增加**退出**；**`.env.development`** 示例说明 **`VITE_ADMIN_AUTH_SKIP=true`**（仅本地无鉴权联调）。
- **用户端**：对话页改为**类 ChatGPT**布局——左侧会话列表 + **新对话**、主区**用户/助手气泡**与流式光标、底部**圆角合成器**与发送钮（主色 **#10a37f**）；切换会话清空本地消息条（后端暂无历史消息列表接口）；**`/system/me`** 独立顶栏返回对话；全局改为**浅色**并去掉深色主题依赖。

### 0.1.16-SNAPSHOT

- **可观测 / 排障**：**`GlobalExceptionHandler`** 对所有 **`@ExceptionHandler`** 分支打日志；**`Exception`** 统一 **`log.error(..., ex)`** 输出类型、消息与完整堆栈（响应体仍为 **`INTERNAL` / `unexpected error`**，避免对客户端泄露实现细节）。
- **catch 补日志**：**`ChatApplicationService`**（SSE 发送与流式编排）、**`MinioFileStorageAdapter`**、**`JobTaskExecutionService`**、**`AccessLogFilter`**（访问日志落库失败）、**`ChatInputGuardService`**（非法正则跳过）、**`MilvusVectorStore`**（关闭客户端）均记录异常详情。
- **日志配置**：**`application.yml`** 增加 **`logging.pattern.console`**（含 **`%wEx`** 堆栈）；**`com.aaron.cloud`** 级别可由环境变量 **`LOGGING_LEVEL_COM_AARON_CLOUD`** 覆盖（默认 **INFO**）。
- **修订说明**：**`.cursorrules`** 新增 **§6.1 全局异常处理**、**§6.2 `catch` 与异常边界**；**§7**、**§9** 与全局异常 / `catch` 规则对齐引用。

### 0.1.15-SNAPSHOT

- **前端 UI**：**`web/user-web`**、**`web/admin-web`** 引入 **Element Plus** + **@element-plus/icons-vue**，全量样式与中文语言包；**`unplugin-auto-import`**（含 **ElementPlusResolver**）与 **`@types/node`**；**`Vite`** 配置 **`@`** 别名。
- **用户端**：深色渐变壳、顶栏 **ElMenu**、对话页 **ElCard** / **ElScrollbar** / 流式输出区样式优化；**MeView** 骨架屏与错误态。
- **管理端**：**`AdminLayout`** 侧栏导航 + 顶栏；登录页全屏渐变 + **ElCard** 表单；用户管理 **ElTable** / **ElMessageBox**；访问日志、审计、计量共用 **`JsonInspector`**（骨架、复制 JSON）。

### 0.1.14-SNAPSHOT

- **前端**：**`web/user-web`**、**`web/admin-web`** 将 **Vite** 升至 **`^6.4.2`**、**`@vitejs/plugin-vue`** 至 **`^6.0.6`**，消除 **`npm audit`** 中 **esbuild**（开发服务器相关 **GHSA-67mh-4wv8-2f99**）的中等风险告警；两栈请在各自目录重新执行 **`npm install`**。

### 0.1.13-SNAPSHOT

- **前端依赖**：在 **`web/.npmrc`** 中配置 **npmmirror**（`registry.npmmirror.com`）及常用二进制镜像（`disturl` / `electron_mirror` / `sass_binary_site`），在 **`web/user-web`**、**`web/admin-web`** 目录执行 **`npm install`** 时会向上解析该配置，加速国内下载。若需切回官方源，可在对应目录执行 **`npm config set registry https://registry.npmjs.org`** 或删除/覆盖 **`registry`** 行。

### 0.1.12-SNAPSHOT

- **数据库**：**`db/mysql/schema_v1.sql`** 中将 **`JSON`** 列改为 **`LONGTEXT`**（语义仍为 JSON 文本），兼容未支持原生 **`JSON`** 类型的 MySQL/MariaDB 实例，避免执行 DDL 报 **1064**。

### 0.1.11-SNAPSHOT

- **配置风格**：**`application.yml`** 按块增加分区注释；**RocketMQ** 的 **`rocketmq.name-server`** 注明支持 **`host1:port1;host2:port2`**（与另一项目 **`spring.mq.rocketmq.namesrv`** 多地址习惯一致）。
- **Milvus**：（历史）曾支持顶层 **`milvus.config.url`** + **`MilvusLegacyUrlEnvironmentPostProcessor`** 映射至 **`com.aaron.cloud.providers.milvus.*`**；**0.1.152** 起已删除，仅保留 **`com.aaron.cloud.providers.milvus`** 直连配置。
- **Redis 说明**：注释区说明另一项目 **`spring.redis.*` / `hostPort`** 与 Spring Boot 3 标准 **`spring.data.redis.*`** 的差异；未默认引入 **`spring-boot-starter-data-redis`**。

### 0.1.10-SNAPSHOT

- **Eureka 解析基建地址**：新增 **`com.aaron.cloud.providers.infra-via-discovery`**（初版默认 **`${AI_INFRA_VIA_DISCOVERY:${AI_DISCOVERY_ENABLED:false}}`**；**0.1.185** 起为 **`${AI_INFRA_VIA_DISCOVERY:false}`** 单 env）；为 true 时 **Milvus** / **MinIO** 优先从 Eureka 按 **`com.aaron.cloud.providers.milvus.service-id`** / **`minio.service-id`**（环境变量可覆盖）取实例 **HTTP 基址**，无实例则回退 **`host`/`endpoint`**；实现类 **`EurekaInfraAddress`**、**`MilvusVectorStore`** / **`MinioClientConfiguration`** 注入 **`DiscoveryClient`**。

### 0.1.9-SNAPSHOT

- **服务发现**：由 **Spring Cloud Alibaba Nacos Discovery** 改为 **Netflix Eureka Client**（**`spring-cloud-starter-netflix-eureka-client`**）；移除 **`spring-cloud-alibaba-dependencies`** 与 Nacos 依赖；**`application.yml`** 使用 **`spring.cloud.discovery.enabled`** / **`eureka.client.enabled`**（默认 **`AI_DISCOVERY_ENABLED=false`**）、**`eureka.client.service-url.defaultZone`**（**`EUREKA_DEFAULT_ZONE`**，默认 `http://127.0.0.1:8761/eureka/`）；**`AiApplication`** 增加 **`@EnableDiscoveryClient`**。

### 0.1.8-SNAPSHOT

- **消息中间件**：异步任务投递由 **RabbitMQ** 改为 **Apache RocketMQ**（**`rocketmq-spring-boot-starter`**）；**`rocketmq.app.*`**、`rocketmq.name-server` / **`producer.group`**；**`JobPublisher`** / **`JobDispatchListener`** 使用 **`RocketMQTemplate`** 与 **`@RocketMQMessageListener`**；默认 **`AiApplication`** 排除 **`RocketMQAutoConfiguration`**，**`rocketmq.app.enabled=true`** 时由 **`JobRocketMqConfiguration`** 按需导入。（**0.1.152** 起已删除 **`AiLegacyRabbitMqToRocketMqEnvironmentPostProcessor`** 对旧 Rabbit 环境变量的映射。）
- **Docker Compose**：**`rocketmq-namesrv`** + **`rocketmq-broker`** 替换原 RabbitMQ 服务。
- **Redis**：当前仓库**未**引入 **`spring-boot-starter-data-redis`**，业务也未使用 Redis；**`application.yml`** 中 **`com.aaron.cloud` 下注释**说明可按需接入（会话、限流、缓存等场景）。

### 0.1.7-SNAPSHOT

- **构建**：`pom.xml` 中 **`java.version`** 由 **26** 调整为 **25**，与常见本机 **JDK 25** 对齐；**`.cursorrules`** / **`PROJECT.md`** 中 JDK 表述同步。
- **修订说明**：**`maven-compiler-plugin`** 显式 **`release=${java.version}`**、**`enablePreview=false`**、**`parameters=true`**；**`.idea/compiler.xml`** 为模块 **Ai** 指定 **bytecodeTargetLevel 25**，避免 IntelliJ 仍使用「源 24 + `--enable-preview`」与 JDK 25 工具链冲突。
- **修订说明**：**`pom.xml`** 增加 **`maven.compiler.enablePreview=false`** / **`maven.compiler.release`**；若 IDEA 仍提示「须接受测试版 Java 规范才能启用 X 实验性功能」：**项目结构 → 项目/模块 → 语言级别** 请选择 **25** 或 **SDK 默认值**，**不要**选 **「25 (Preview)」**；然后 **Maven → 重新加载项目** 再启动。

### 0.1.6-SNAPSHOT

- **配置**：统一 **`com.aaron.cloud.providers.*`**（auth / vector-store / model / file-storage / notification）及 **`com.aaron.cloud.auth.jwt-local.secret`**、Milvus/OpenAI/MinIO 子块；**`spring.security.oauth2.resourceserver.jwt.issuer-uri`** 供 **`oauth2-resource`**。（**0.1.151** 起移除 **`rag.milvus.enabled`** 兼容；**0.1.152** 起移除全部 **`EnvironmentPostProcessor`** 遗留映射，见该版变更记录。）
- **依赖**：**`spring-boot-starter-oauth2-resource-server`**、**`io.milvus:milvus-sdk-java`**、**`io.minio:minio`**。
- **文件存储**：**`FileStoragePort`** 的 **`LocalFileStorageAdapter`** / **`MinioFileStorageAdapter`**；**`FileRestController`** 走端口实现。
- **异常**：**`AccessDeniedException`** → 403；**`ErrorCodes.EX_MSG_LOGIN_NAME_CONFLICT`** → 409（**`LOGIN_NAME_CONFLICT`**，文案「该登录名已被占用」）。
- **前端管理端**：**`/users`** 用户列表与创建/启停/删除；**`/login`** 对接 **`jwt-local`** 并写入 **`ai_admin_access_token`**；**axios** 请求头附带 **Bearer**。

### 0.1.5-SNAPSHOT

- **数据库**：新增 **`db/mysql/schema_v1.sql`**（无物理外键；`lnk_*` 与会话/消息、RAG 文档分块等）；默认租户 **`default` id=1** 幂等种子数据。
- **后端**：按 **§4.1** 包结构落地 **`common`** 持久化（MyBatis-Plus + Join + **`mybatis-plus-jsqlparser` 分页**）、**`gateway`**（租户上下文 `X-Tenant-Id` / `X-Tenant-Code`、**`X-Device-Id`**、**accesslog** 落库）、**`identity`**（Security 放行 OpenAPI + CORS）、**`model`**（本地/远程 **`ModelInvokePort`**、mock 流式、计量占位写入）、**`chat`**（会话 CRUD、SSE、意图占位接 **RAG**）、**`rag`**（知识库、索引任务入队/同步执行、**Milvus 占位 `NoOpVectorStore`**）、**`job`**（当时为 RabbitMQ；**0.1.8** 起见 **RocketMQ** 配置）、**`mcp`**（工具白名单 **`echo`**）、**`eval`**（护栏规则同步拦截）、**`file`** / **`notification`**（占位 API）、**`remoting`**（**`com.aaron.cloud.remoting.mode=remote`** 时 Feign + 出站租户/Device 透传；默认 **`local`**）、**springdoc**、**Actuator**。
- **前端**：新增 **`web/user-web`**（对话页 + `localStorage` **deviceId**、fetch SSE）、**`web/admin-web`**（访问日志/审计/计量只读列表占位）；构建 **`vite build`**（未强制 `vue-tsc`）。
- **工程**：**`pom.xml`** 引入 Spring Cloud OpenFeign、LoadBalancer、Resilience4j、服务发现（当时为 Nacos，**0.1.9** 起为 **Eureka**）、AMQP（**0.1.8** 起改为 **RocketMQ**）、Security、Actuator；**`maven-compiler-plugin` 显式 3.11.0** 以兼容较旧 Maven Wrapper 环境。
- **测试**：集成测试 **`@ActiveProfiles("integration")`** + **`application-integration.yml`**；`-Dai.integration=true` 时校验 **`SysTenantRepository.findAllActive`**。

### 0.1.4-SNAPSHOT

- **修订说明**：**`.cursorrules`** §4.2 明确——子模块以 **`com.aaron.cloud` 下包目录**区分；**暂不**拆 Maven 多模块；**单一启动类**聚合；目录结构面向后期**整包迁移**到新子模块/新仓库时尽量少改依赖与扫描即可运行。
- **修订说明**：**`.cursorrules`** §1 / §4.1.3 / §7 / §8 / §9 强化——**尽量使用枚举**表达状态、类型、阶段等有限集合业务语义；**禁止**在业务与持久化路径中**写死**魔法数字、裸字符串作通用码值；DTO/MQ 等与库表一致处同责。
- **规则**：**`.cursorrules`** §4.1.1 / §4.1.3 / §7 / §8 / §9 约定——库表**状态/类型码**须由 **`com.aaron.cloud.common.api.enums`** 下 Java **枚举**（推荐 **`@EnumValue`**）集中映射；**`application.yml`** 配置 **`type-enums-package`** 与默认枚举 **TypeHandler**。

### 0.1.3-SNAPSHOT

- **后端骨架**：引入 **Spring Boot 3.4.5**、**MyBatis-Plus**、**MyBatis-Join**、**MySQL Connector/J**、**Lombok**；入口 **`com.aaron.cloud.AiApplication`**；`application.yml` 数据源（`MYSQL_*` 环境变量可覆盖）。
- **common 持久化（底层）**：`MybatisPlusConfiguration`（`@MapperScan`）、`CommonMetaObjectHandler`（`created_at`/`updated_at` UTC 填充）；**`common.tenant`** 下 **`SysTenant`** 实体、**`SysTenantMapper`**、**`SysTenantRepository`**（按 id / code 查询与插入）。
- **测试**：`BuildSanityTest`；完整上下文集成测试 **`AiApplicationIntegrationTest`** 需 `-Dai.integration=true` 且本机 MySQL 已建库并执行 **`db/mysql/schema_v1.sql`**。
- **构建环境**：仓库目标 **JDK 25**；建议使用 **Maven 3.9+** 与 **`JAVA_HOME`** 指向 JDK 25。

### 0.1.2-SNAPSHOT

- **数据库**：`db/mysql/schema_v1.sql` 去除全部 **`FOREIGN KEY`**；会话与消息改为 **`lnk_chat_conversation_message`**；知识库与文档、文档与分块分别改为 **`lnk_rag_kb_document`**、**`lnk_rag_document_chunk`**。
- **规则**：**`.cursorrules`** §4.1.3 增补 MySQL 物理建模约定，§8 增补禁止新增物理外键。

### 0.1.1-SNAPSHOT

- **数据库**：新增 MySQL 8 初始化 DDL **`db/mysql/schema_v1.sql`**（首版全量表设计）。

### 0.1.0（开发）

- **pom**：`<version>` 为 **`0.1.0-SNAPSHOT`**（0.1 开发线起始）。
- **文档**：新增 **`PROJECT.md`**，建立项目说明与版本记录机制。
- **规则**：**`.cursorrules`** 增加 **§10** 及后续架构/中台能力章节。
