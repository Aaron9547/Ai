package com.aaron.cloud.common.api.enums;

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
    /** C 端 {@code POST /open/v1/auth/register} 是否允许（按注册目标租户判断） */
    AUTH_OPEN_REGISTRATION(
            "AUTH_OPEN_REGISTRATION",
            "自助注册",
            SettingValueKind.BOOLEAN,
            "true",
            false),
    /** Coze OpenAPI 域名，如 {@code https://api.coze.cn}；留空则运行时使用官方默认 */
    TRAVEL_REIMBURSE_COZE_DOMAIN(
            "TRAVEL_REIMBURSE_COZE_DOMAIN",
            "出差报销 Coze 域名",
            SettingValueKind.STRING,
            "https://api.coze.cn",
            false),
    /** 文档解析工作流 PAT，Bearer */
    TRAVEL_REIMBURSE_DOC_COZE_API_KEY(
            "TRAVEL_REIMBURSE_DOC_COZE_API_KEY",
            "文档解析工作流 API Key",
            SettingValueKind.STRING,
            "",
            true),
    TRAVEL_REIMBURSE_DOC_WORKFLOW_ID(
            "TRAVEL_REIMBURSE_DOC_WORKFLOW_ID",
            "文档解析工作流 ID",
            SettingValueKind.STRING,
            "",
            false),
    TRAVEL_REIMBURSE_PLAN_COZE_API_KEY(
            "TRAVEL_REIMBURSE_PLAN_COZE_API_KEY",
            "行程规划工作流 API Key",
            SettingValueKind.STRING,
            "",
            true),
    TRAVEL_REIMBURSE_PLAN_WORKFLOW_ID(
            "TRAVEL_REIMBURSE_PLAN_WORKFLOW_ID",
            "行程规划工作流 ID",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 用户分层记忆写入 Milvus 时使用的嵌入模型：值为 {@code sys_llm_model.id}（须为 VECTOR、启用）；留空则退化为哈希占位向量。
     */
    MEMORY_EMBEDDING_VECTOR_MODEL_ID(
            "MEMORY_EMBEDDING_VECTOR_MODEL_ID",
            "记忆嵌入模型 id（VECTOR）",
            SettingValueKind.STRING,
            "",
            false),
    /**
     * 主对话 RAG/联网注入及短期记忆预算（JSON 对象，键见 {@link ChatPromptLimitsRuntime}，含 {@code historyMaxMessages}
     * / {@code historyMaxCharsPerMessage} / {@code historyTotalMaxChars}；空对象表示代码默认）。
     */
    CHAT_PROMPT_LIMITS_JSON(
            "CHAT_PROMPT_LIMITS_JSON",
            "对话 system 体量（JSON，{}=默认）",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 用户记忆策略可调部分（JSON 对象，键见 {@link MemoryPolicyRuntime}；Milvus 开关与队列名仍由 yml 配置）。
     */
    MEMORY_POLICY_JSON(
            "MEMORY_POLICY_JSON",
            "用户记忆策略（JSON，{}=默认）",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 对话发送前同步护栏（JSON 对象：enabled、minUserTextChars、maxUserTextChars、blockedReplyTemplate、sensitiveWords、promptInjectionRegexPatterns）。
     */
    CHAT_INPUT_GUARD_JSON(
            "CHAT_INPUT_GUARD_JSON",
            "输入护栏（JSON，{}=默认）",
            SettingValueKind.STRING,
            "{}",
            false),
    /**
     * 联网前置检索（Bot）轮数：1～10 的十进制数字字符串；每轮独立请求一次。未配置或非法时按默认 3。
     */
    WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT(
            "WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT",
            "联网检索轮数（1～10）",
            SettingValueKind.STRING,
            "3",
            false),
    /**
     * 与 {@link #WEB_SEARCH_GROUNDING_MULTI_ROUND_COUNT} 对齐的 JSON 字符串数组：下标 i 拼在第 i 轮用户检索问句之后；建议非首项含换行以便与正文分段。
     */
    WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON(
            "WEB_SEARCH_GROUNDING_ROUND_SUFFIXES_JSON",
            "各轮问句后缀（JSON 数组）",
            SettingValueKind.STRING,
            "[]",
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

    public enum SettingValueKind {
        BOOLEAN,
        /** 短文本（如 URL、工作流 id、PAT）；允许空串表示未配置 */
        STRING
    }
}
