package com.aaron.cloud.common.api.enums.tenant;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 租户级运行时系统配置项（存 {@code ten_runtime_setting.setting_key}）；扩展能力时在此新增枚举，禁止在业务分支中写死键名字符串。
 */
@Getter
@RequiredArgsConstructor
public enum TenantRuntimeSettingKey {
    /** C 端注册验证码发送（邮件 SMTP、标题/正文模板等 JSON；管理端「外观与模型调用」配置） */
    AUTH_REGISTER_VERIFICATION_JSON(
            "AUTH_REGISTER_VERIFICATION_JSON",
            "注册验证码发送（JSON）",
            SettingValueKind.STRING,
            "{}",
            true),
    /** C 端 {@code POST /open/v1/auth/register} 是否允许（按注册目标租户判断） */
    AUTH_OPEN_REGISTRATION(
            "AUTH_OPEN_REGISTRATION",
            "自助注册",
            SettingValueKind.BOOLEAN,
            "false",
            false),
    /**
     * 用户分层记忆写入 Milvus 时使用的嵌入模型：值为 {@code sys_llm_model.id}（须为 VECTOR、启用）；留空则退化为哈希占位向量。
     */
    MEMORY_EMBEDDING_VECTOR_MODEL_ID(
            "MEMORY_EMBEDDING_VECTOR_MODEL_ID",
            "嵌入模型",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 对话前置联网检索使用的模型：值为 {@code sys_llm_model.id}（须为 WEB_SEARCH、启用）；留空则按 {@code sort_order} 取租户默认联网实例。
     */
    WEB_SEARCH_GROUNDING_MODEL_ID(
            "WEB_SEARCH_GROUNDING_MODEL_ID",
            "联网模型",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 启用的内置固定联网源：JSON 字符串数组，元素为 {@link com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource#getCode()}。
     * 与 {@link #WEB_SEARCH_GROUNDING_MODEL_ID} 组合；至少启用一类源由专页保存校验。
     */
    WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON(
            "WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON",
            "内置固定源",
            SettingValueKind.STRING,
            "[]",
            false),
    /**
     * 联网问句重写 / 固定源三关键词抽取模型：值为 {@code sys_llm_model.id}（须为启用的 LANGUAGE 或火山 Ark
     * WEB_SEARCH）；LANGUAGE 走提示词 + 流式 LLM，WEB_SEARCH 单次 Ark Bot 非流式调用；留空则按 {@code sort_order}
     * 取租户默认对话模型（LANGUAGE）。
     */
    WEB_SEARCH_QUERY_REWRITE_MODEL_ID(
            "WEB_SEARCH_QUERY_REWRITE_MODEL_ID",
            "问句重写",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 已改为 {@link #WEB_SEARCH_QUERY_REWRITE_MODEL_ID}；读路径兼容，写路径管理端保存时清空。
     */
    @Deprecated
    WEB_SEARCH_QUERY_REWRITE_LANGUAGE_MODEL_ID(
            "WEB_SEARCH_QUERY_REWRITE_LANGUAGE_MODEL_ID",
            "（已废弃）问句重写语言模型 id",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 已改为 {@link #WEB_SEARCH_QUERY_REWRITE_MODEL_ID}；读路径兼容，写路径管理端保存时清空。
     */
    @Deprecated
    WEB_SEARCH_QUERY_REWRITE_WEB_SEARCH_MODEL_ID(
            "WEB_SEARCH_QUERY_REWRITE_WEB_SEARCH_MODEL_ID",
            "（已废弃）问句重写联网模型 id",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 启用的联网模型 id 列表（JSON 十进制数组）；与 {@link #WEB_SEARCH_GROUNDING_MODEL_ID} 首项对齐。
     * 管理端「联网检索源」可多选联网模型；对话联网与今日洞察均读取本键。
     */
    WEB_SEARCH_GROUNDING_MODEL_IDS_JSON(
            "WEB_SEARCH_GROUNDING_MODEL_IDS_JSON",
            "联网模型列表",
            SettingValueKind.STRING,
            "[]",
            false),
    /**
     * 主对话 RAG/联网注入及短期记忆预算（JSON 对象，键见 {@link ChatPromptLimitsRuntime}，含 {@code historyMaxMessages}
     * / {@code historyMaxCharsPerMessage} / {@code historyTotalMaxChars}；空对象表示代码默认）。
     */
    CHAT_PROMPT_LIMITS_JSON(
            "CHAT_PROMPT_LIMITS_JSON",
            "对话上下文",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 用户记忆策略可调部分（JSON 对象，键见 {@link MemoryPolicyRuntime}；Milvus 开关与队列名仍由 yml 配置）。
     */
    MEMORY_POLICY_JSON(
            "MEMORY_POLICY_JSON",
            "长期记忆",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 对话发送前同步护栏（JSON 对象：enabled、minUserTextChars、maxUserTextChars、blockedReplyTemplate、sensitiveWords、promptInjectionRegexPatterns）。
     */
    CHAT_INPUT_GUARD_JSON(
            "CHAT_INPUT_GUARD_JSON",
            "输入护栏",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 出站超时、重试、退避、租户隔离等（JSON 对象；与 {@code ai.outbound} 进程基线深度合并；{@code circuitBreaker}
     * 子树仅来自进程基线，租户 JSON 中同名键忽略）。
     */
    OUTBOUND_RESILIENCE_JSON(
            "OUTBOUND_RESILIENCE_JSON",
            "出站韧性（JSON，{}=仅基线）",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 联网前置检索（Bot）轮数：1～10 的十进制数字字符串；每轮独立请求一次。未配置或非法时按默认 3。
     */
    WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT(
            "WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT",
            "检索轮数",
            SettingValueKind.STRING,
            "3",
            false),
    /**
     * 与 {@link #WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT} 对齐的 JSON 字符串数组：下标 i 拼在第 i 轮用户检索问句之后；建议非首项含换行以便与正文分段。
     */
    WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON(
            "WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON",
            "各轮检索后缀",
            SettingValueKind.STRING,
            "[]",
            false),
    /**
     * 联网检索 Redis 缓存（JSON 对象）：{@code enabled}、{@code freshHours}/{@code warmHours}/{@code staleHours}（默认 6/24/48）、
     * {@code semanticEnabled}、{@code similarityThreshold}、{@code indexMaxEntries}、{@code conversationReuseHours}；{@code {}} 走内置默认。
     */
    WEB_SEARCH_GROUNDING_CACHE_JSON(
            "WEB_SEARCH_GROUNDING_CACHE_JSON",
            "联网缓存",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 内置固定联网源出站代理（JSON）：{@code enabled}、{@code host}、{@code port}、{@code type}（HTTP/SOCKS）；
     * 未启用时走进程 {@code ai.websearch.fixed.*} 或直连。租户 Shell「外观与模型调用」配置。
     */
    WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON(
            "WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON",
            "固定源代理",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * RAG / Milvus 向量维数（十进制整数，如 2048）：与嵌入模型 {@code dimensions}、Milvus FloatVector 一致。
     * 仅在租户 Shell「外观与模型调用」配置；首次写入或租户已有 {@code rag_chunk} 后不可修改。
     */
    RAG_VECTOR_DIMENSION(
            "RAG_VECTOR_DIMENSION",
            "RAG 向量维数（锁定后不可改）",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * RAG 检索模式：{@code milvus} / {@code milvus_es_hybrid}；留空则使用进程 {@code ai.rag.retrieval-mode}。
     */
    RAG_RETRIEVAL_MODE(
            "RAG_RETRIEVAL_MODE",
            "RAG 检索模式",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * RAG 检索调优（JSON 对象，键见 {@link com.aaron.cloud.common.tenant.runtime.RagRetrievalTuningRuntime}：问句改写语义门控、
     * 简单/复杂分流、混合 LTR 与 {@code activeLtrFileObjectId} 指针）；空对象表示代码默认。
     */
    RAG_RETRIEVAL_TUNING_JSON(
            "RAG_RETRIEVAL_TUNING_JSON",
            "RAG 检索调优（JSON）",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 知识库站点爬取快捷档位：{@code CONSERVATIVE} / {@code BALANCED}（默认）/ {@code AGGRESSIVE} / {@code CUSTOM}；
     * 仅在租户 Shell「外观与模型调用」配置。
     */
    SITE_CRAWL_PRESET(
            "SITE_CRAWL_PRESET",
            "站点爬取档位",
            SettingValueKind.STRING,
            "BALANCED",
            false),
    /**
     * 站点爬取运行时策略 JSON（discovery / politeness / fetch / extract / ingest）；{@code CUSTOM} 时必填有效对象；
     * 非 CUSTOM 时可存预设模板快照或 {@code {}}（由 {@code SiteCrawlPolicyResolver} 合并）。
     */
    SITE_CRAWL_RUNTIME_JSON(
            "SITE_CRAWL_RUNTIME_JSON",
            "站点爬取运行时（JSON）",
            SettingValueKind.STRING,
            "{}",
            false),
    /** 是否每日通过联网大模型刷新空会话推荐问句（热点兜底池）。 */
    CHAT_STARTER_DAILY_HOT_ENABLED(
            "CHAT_STARTER_DAILY_HOT_ENABLED",
            "每日热点推荐（联网）",
            SettingValueKind.BOOLEAN,
            "true",
            false),
    /**
     * Spring 6 段 cron（Asia/Shanghai）；进程每分钟 tick 一次，命中该表达式时为本租户拉取热点。默认每天 06:00。
     */
    CHAT_STARTER_DAILY_HOT_CRON(
            "CHAT_STARTER_DAILY_HOT_CRON",
            "每日热点拉取时刻（cron）",
            SettingValueKind.STRING,
            "0 0 6 * * *",
            false),
    /** 个人知识星球：对话沉淀、周报与周一邮件（管理端「租户能力与外观」配置）。 */
    KNOWLEDGE_PLANET_ENABLED(
            "KNOWLEDGE_PLANET_ENABLED",
            "个人知识星球",
            SettingValueKind.BOOLEAN,
            "false",
            false),
    /** 是否启用知识星球周报邮件推送（通道见消息中心 KNOWLEDGE_PLANET_WEEKLY）。 */
    KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED(
            "KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED",
            "知识星球周报邮件开关",
            SettingValueKind.BOOLEAN,
            "true",
            false),
    /** 迁移/默认种子；Cron 以管理端「定时任务」为准，Shell 不再编辑。 */
    KNOWLEDGE_PLANET_WEEKLY_COMPUTE_CRON(
            "KNOWLEDGE_PLANET_WEEKLY_COMPUTE_CRON",
            "知识星球周一方案计算（cron，定时任务专管）",
            SettingValueKind.STRING,
            "0 0 3 * * MON",
            false),
    /** 迁移/默认种子；Cron 以管理端「定时任务」为准，Shell 不再编辑。 */
    KNOWLEDGE_PLANET_WEEKLY_EMAIL_CRON(
            "KNOWLEDGE_PLANET_WEEKLY_EMAIL_CRON",
            "知识星球周一邮件推送（cron，定时任务专管）",
            SettingValueKind.STRING,
            "0 0 9 * * MON",
            false),
    KNOWLEDGE_PLANET_EMAIL_JSON(
            "KNOWLEDGE_PLANET_EMAIL_JSON",
            "知识星球周报邮件（JSON）",
            SettingValueKind.STRING,
            "{}",
            true),
    KNOWLEDGE_PLANET_DIGEST_MODEL_ID(
            "KNOWLEDGE_PLANET_DIGEST_MODEL_ID",
            "知识星球沉淀/周报模型 id",
            SettingValueKind.STRING,
            "",
            false),
    /** 周报计算前是否联网检索书目摘要（Pre-LLM，每用户每周至多 1 次）。 */
    KNOWLEDGE_PLANET_WEEKLY_BOOK_SEARCH_ENABLED(
            "KNOWLEDGE_PLANET_WEEKLY_BOOK_SEARCH_ENABLED",
            "知识星球周报荐书联网",
            SettingValueKind.BOOLEAN,
            "true",
            false),
    /** 本周知识节点数低于该值且无学习者画像时跳过 LLM 周报。 */
    KNOWLEDGE_PLANET_WEEKLY_MIN_NODES(
            "KNOWLEDGE_PLANET_WEEKLY_MIN_NODES",
            "知识星球周报最少节点数",
            SettingValueKind.STRING,
            "2",
            false),
    /** 对话 MCP 工具调用最大轮数（模型返回 tool_calls 后重入模型的次数上限）。 */
    MCP_CHAT_MAX_TOOL_ROUNDS(
            "MCP_CHAT_MAX_TOOL_ROUNDS",
            "MCP 对话最大工具轮数",
            SettingValueKind.STRING,
            "5",
            false),
    /** 单次 MCP tools/call 超时（秒）。 */
    MCP_CHAT_TOOL_TIMEOUT_SECONDS(
            "MCP_CHAT_TOOL_TIMEOUT_SECONDS",
            "MCP 工具调用超时（秒）",
            SettingValueKind.STRING,
            "60",
            false),
    /** 单次 tool 结果写入对话上下文的最大字符数（防 read_url 等超大响应）。 */
    MCP_CHAT_TOOL_RESULT_MAX_CHARS(
            "MCP_CHAT_TOOL_RESULT_MAX_CHARS",
            "MCP 工具结果截断（字符）",
            SettingValueKind.STRING,
            "8000",
            false);

    @EnumValue
    private final String storage;

    private final String descriptionZh;
    private final SettingValueKind valueKind;
    private final String defaultValueText;

    /**
     * 管理端列表是否默认遮罩「值」列（密钥等）；仍为明文 JSON 返回，仅提示前端做显隐；新增疑似密钥项时须在此显式置 true。
     */
    private final boolean maskSensitiveInAdminUi;

    public static Optional<TenantRuntimeSettingKey> fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = raw.trim();
        return Arrays.stream(values()).filter(k -> k.storage.equalsIgnoreCase(t)).findFirst();
    }

    /**
     * 不在管理端「系统参数（本租户）」列表展示；请在对应专页配置（如意图识别、租户外观与模型调用）。
     */
    public boolean excludedFromAdminRuntimeList() {
        return switch (this) {
            case OUTBOUND_RESILIENCE_JSON,
                    RAG_VECTOR_DIMENSION,
                    RAG_RETRIEVAL_MODE,
                    RAG_RETRIEVAL_TUNING_JSON,
                    MEMORY_EMBEDDING_VECTOR_MODEL_ID,
                    WEB_SEARCH_GROUNDING_MODEL_ID,
                    WEB_SEARCH_GROUNDING_MODEL_IDS_JSON,
                    WEB_SEARCH_GROUNDING_FIXED_SOURCES_JSON,
                    WEB_SEARCH_QUERY_REWRITE_MODEL_ID,
                    WEB_SEARCH_QUERY_REWRITE_LANGUAGE_MODEL_ID,
                    WEB_SEARCH_QUERY_REWRITE_WEB_SEARCH_MODEL_ID,
                    CHAT_PROMPT_LIMITS_JSON,
                    MEMORY_POLICY_JSON,
                    CHAT_INPUT_GUARD_JSON,
                    WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT,
                    WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON,
                    WEB_SEARCH_GROUNDING_CACHE_JSON,
                    WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON,
                    SITE_CRAWL_PRESET,
                    SITE_CRAWL_RUNTIME_JSON,
                    CHAT_STARTER_DAILY_HOT_ENABLED,
                    CHAT_STARTER_DAILY_HOT_CRON,
                    KNOWLEDGE_PLANET_ENABLED,
                    KNOWLEDGE_PLANET_WEEKLY_EMAIL_ENABLED,
                    KNOWLEDGE_PLANET_WEEKLY_COMPUTE_CRON,
                    KNOWLEDGE_PLANET_WEEKLY_EMAIL_CRON,
                    KNOWLEDGE_PLANET_EMAIL_JSON,
                    KNOWLEDGE_PLANET_DIGEST_MODEL_ID,
                    AUTH_REGISTER_VERIFICATION_JSON -> true;
            default -> false;
        };
    }

    /** 管理端字段中文简述（运行参数列表等）；校验异常请用 {@link TenantRuntimeSettingMessages#fieldLabel}。 */
    public String getLabel() {
        return descriptionZh;
    }

    public enum SettingValueKind {
        BOOLEAN,
        /** 短文本（如 URL、工作流 id、PAT）；允许空串表示未配置 */
        STRING
    }
}
