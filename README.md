# Ai（`com.aaron.cloud:Ai`）

以 **AI 对话** 为核心的多租户 **AI 中台**：多模型路由、RAG（Milvus / Elasticsearch）、联网检索、意图与工具编排、用户画像与长记忆、管理端运营配置等。本仓库为 **前后端同仓**：后端 Spring Boot，前端两个独立 SPA（用户端 / 管理端）。

| 项 | 说明 |
|----|------|
| **当前构件版本** | `0.1.229-SNAPSHOT`（与 `pom.xml` `<version>` 一致） |
| **产品说明与演进** | 根目录 [`PROJECT.md`](PROJECT.md)（含「变更记录」与功能专节） |
| **编码与架构硬约束** | 根目录 [`.cursorrules`](.cursorrules)（协作真源） |
| **数据库基线** | [`db/mysql/schema_v1.sql`](db/mysql/schema_v1.sql)；执行说明见 [`db/mysql/README.md`](db/mysql/README.md) |

---

## 技术栈与主要依赖版本

### 后端（Maven）

| 组件 | 版本 |
|------|------|
| **JDK** | **25**（`maven-enforcer-plugin` 要求 `[25,)`；关闭预览特性） |
| **Maven** | **≥ 3.6.3**（建议用仓库自带 `mvnw` 或本机 3.9.x） |
| **Spring Boot** | **3.4.5**（`spring-boot-starter-parent`） |
| **Spring Cloud** | **2024.0.0**（BOM `spring-cloud-dependencies`） |
| **MyBatis-Plus** | **3.5.16**（`mybatis-plus-spring-boot3-starter` / `mybatis-plus-jsqlparser`） |
| **MyBatis-Plus-Join** | **1.5.7** |
| **SpringDoc OpenAPI** | **2.8.5**（`springdoc-openapi-starter-webmvc-ui`） |
| **RocketMQ Spring** | **2.3.3**（`rocketmq-spring-boot-starter`） |
| **Milvus Java SDK** | **2.5.4** |
| **Elasticsearch Java API Client** | **8.15.5** |
| **MinIO Java SDK** | **8.5.17** |
| **Apache Tika** | **2.9.2**（`tika-core` + `tika-parsers-standard-package`） |
| **Jsoup** | **1.18.3** |
| **Commons IO** | **2.18.0**（显式声明，避免传递依赖过旧） |

集成栈（版本由 Spring Cloud BOM 管理）：**OpenFeign**、**Spring Cloud LoadBalancer**、**Resilience4j Circuit Breaker**、**Netflix Eureka Client**；另有 **Spring Security** + **OAuth2 Resource Server**、**Redis**、**Actuator** 等，详见 `pom.xml`。

### 前端（两个 SPA，各自 `package.json`）

| 组件 | 版本（`^` 表示 semver 范围，以 lockfile 为准） |
|------|-----------------------------------------------|
| **Vue** | ^3.5.13 |
| **Vite** | ^6.4.2 |
| **TypeScript** | ~5.7.2 |
| **Vue Router** | ^4.5.0 |
| **Pinia** | ^2.3.0 |
| **vue-i18n** | ^9.14.5 |
| **Element Plus** | ^2.9.9 |
| **axios** | ^1.7.9 |
| **markdown-it** | ^14.x |
| **DOMPurify** | ^3.x |
| **ECharts**（管理端） | ^5.6.0 |
| **@vueuse/core**（管理端） | ^12.5.0 |

---

## 仓库目录结构（摘要）

```text
Ai/
├── pom.xml
├── PROJECT.md                 # 产品说明、运行时约定、变更记录
├── .cursorrules               # 架构 / 包边界 / 流程硬约束
├── docker-compose.yml         # 本地示例：MySQL、RocketMQ 等
├── db/mysql/
│   ├── schema_v1.sql          # 新库结构真源
│   ├── migrate_*.sql          # 可选增量迁移
│   └── README.md
├── src/main/java/com/aaron/cloud/
│   ├── AiApplication.java
│   ├── chat/                  # 对话编排、意图、联网检索等
│   ├── common/                # 租户、安全、持久化、画像与记忆等共享域
│   ├── model/                 # 大模型配置与管理端元数据
│   ├── rag/                   # RAG、向量与 ES 检索
│   ├── mcp/                   # 工具 / MCP
│   ├── gateway/               # 网关侧策略等（若启用）
│   ├── identity/              # 身份与租户运行参数 API 等
│   ├── job/                   # 异步任务
│   ├── file/                  # 文件元数据等
│   ├── eval/                  # 评测相关
│   ├── notification/          # 通知
│   └── remoting/              # 远程调用装配
├── src/main/resources/
│   ├── application.yml        # 进程启动与中间件（键旁注释为真源之一）
│   └── llm-admin-meta_*.properties
├── src/test/java/
├── web/
│   ├── user-web/              # C 端：对话等（Vite SPA）
│   └── admin-web/             # 管理端：配置与审计（Vite SPA）
└── mvnw / mvnw.cmd            # 推荐使用，满足 enforcer 对 Maven 版本要求
```

顶层 Java 包以 **`.cursorrules` §4.1** 清单为准；上表为常见入口速览。

---

## 环境与快速开始

1. **JDK 25**：`JAVA_HOME` 与 IDE 项目 SDK 指向 JDK 25+（见 `pom.xml` enforcer 提示）。
2. **数据库**：按 [`db/mysql/README.md`](db/mysql/README.md) 初始化；空库优先执行 **`schema_v1.sql`**。
3. **中间件**：连接信息以 **`src/main/resources/application.yml`** 及环境变量为准；本地可参考根目录 **`docker-compose.yml`**（MySQL、RocketMQ 等）。
4. **按租户可调参数**：运行时策略（对话提示上限、记忆策略、输入护栏、联网后缀等）在库表 **`ten_runtime_setting`**（**`TenantRuntimeSettingKey`**），管理端「租户运行参数」维护；与 yml 分层见 **`.cursorrules` §3.8** 与 **`PROJECT.md`「运行时配置」**。

### 构建后端

```bash
./mvnw -q -DskipTests package
# Windows: mvnw.cmd -q -DskipTests package
```

### 运行前端（分别在各自目录）

```bash
cd web/user-web && npm install && npm run dev
cd web/admin-web && npm install && npm run dev
```

用户端对话路由为 **`/{租户编码}/chat`**（`租户编码` = `sys_tenant.code`），详见 **`PROJECT.md`**「★ 用户端路由与租户」。

---

## 近期演进摘要（摘自 `PROJECT.md` 变更记录）

以下内容随版本迭代；**完整条目以 [`PROJECT.md`](PROJECT.md) 顶部 `### x.y.z-SNAPSHOT` 为准**。

- **0.1.229**：租户运行参数扩展（对话提示上限、记忆策略、输入护栏 JSON）；管理端租户运行参数分页检索；主链多轮 **history** 注入与截断；助手回合摘要 **`contentSummary`** 与会话占位标题更新；Markdown 代码块复制；`.cursorrules` 与全局 user rules 关于 `*.md` 的冲突说明。
- **0.1.228**：`EnvironmentPostProcessor` 改为 **`META-INF/spring.factories`** 注册，修复发现未桥接问题。
- **0.1.224 及更早**：联网搜索（`WEB_SEARCH`）、`integration_backend` 列合并、仪表盘与 LLM 管理端 i18n、意图处理器插件化等——见 **`PROJECT.md`** 对应节。

---

## API 与联调

- **OpenAPI / Swagger UI**：由 **SpringDoc** 暴露（具体路径以运行实例与 `application.yml` 为准）。
- **路径约定**：对外 REST 的 **`/open/**`**（免 JWT）与 **`/api/**`**（须 JWT）划分见 **`.cursorrules` §6.0** 与 **`PROJECT.md`**。

---

## 参与协作

- 修改可运行产物（Java / 前端源码 / `db/mysql` / `pom.xml` / `application*.yml` 等）时，须同步更新 **`PROJECT.md`「变更记录」** 与 **`pom.xml` `<version>`** 的 bump 规则，见 **`PROJECT.md`「版本策略」** 与 **`.cursorrules` §9 / §10**。
