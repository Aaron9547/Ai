package com.aaron.cloud.common.api.enums.infra;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 平台级系统参数（存 {@code sys_platform_setting.setting_key}）；全租户共享，管理端「平台系统参数」维护，免重启生效。
 */
@Getter
@RequiredArgsConstructor
public enum PlatformSettingKey {
    TENANT_RUNTIME_CACHE_TTL_SECONDS(
            "TENANT_RUNTIME_CACHE_TTL_SECONDS",
            "租户运行参数 Redis 读穿缓存 TTL（秒）",
            SettingValueKind.INTEGER,
            "600"),
    PROMPT_TEMPLATE_CACHE_TTL_SECONDS(
            "PROMPT_TEMPLATE_CACHE_TTL_SECONDS",
            "提示词模板 Redis 读穿缓存 TTL（秒）",
            SettingValueKind.INTEGER,
            "600"),
    LLM_USAGE_REDIS_POLL_MS(
            "LLM_USAGE_REDIS_POLL_MS",
            "LLM 用量 Redis 队列轮询间隔（毫秒，RocketMQ 关闭时）",
            SettingValueKind.INTEGER,
            "250"),
    MEMORY_ABSTRACT_REDIS_POLL_MS(
            "MEMORY_ABSTRACT_REDIS_POLL_MS",
            "用户记忆抽象层 Redis 队列轮询间隔（毫秒）",
            SettingValueKind.INTEGER,
            "400"),
    AUTH_EMAIL_CODE_TTL_SECONDS(
            "AUTH_EMAIL_CODE_TTL_SECONDS",
            "注册验证码有效期（秒）",
            SettingValueKind.INTEGER,
            "600"),
    AUTH_EMAIL_SEND_COOLDOWN_SECONDS(
            "AUTH_EMAIL_SEND_COOLDOWN_SECONDS",
            "注册验证码发送冷却时间（秒）",
            SettingValueKind.INTEGER,
            "60"),
    OBSERVABILITY_ENABLED(
            "OBSERVABILITY_ENABLED", "可观测性事件采集开关", SettingValueKind.BOOLEAN, "true"),
    OBSERVABILITY_RETENTION_DAYS(
            "OBSERVABILITY_RETENTION_DAYS", "可观测性事件保留天数", SettingValueKind.INTEGER, "90"),
    OBSERVABILITY_PURGE_BATCH_SIZE(
            "OBSERVABILITY_PURGE_BATCH_SIZE", "可观测性清理单批删除行数", SettingValueKind.INTEGER, "5000"),
    OBSERVABILITY_QUEUE_CAPACITY(
            "OBSERVABILITY_QUEUE_CAPACITY", "可观测性异步写入队列容量", SettingValueKind.INTEGER, "1024"),
    OBSERVABILITY_RETENTION_CRON(
            "OBSERVABILITY_RETENTION_CRON",
            "可观测性历史数据清理 Cron（6 段，Asia/Shanghai）",
            SettingValueKind.STRING,
            "0 15 4 * * *"),
    RAG_QA_ENABLED("RAG_QA_ENABLED", "RAG 质量评测总开关", SettingValueKind.BOOLEAN, "true"),
    RAG_QA_JUDGE_ENABLED(
            "RAG_QA_JUDGE_ENABLED", "RAG 质量评测 LLM 裁判开关", SettingValueKind.BOOLEAN, "true"),
    RAG_QA_RETENTION_DAYS("RAG_QA_RETENTION_DAYS", "RAG 质量评测记录保留天数", SettingValueKind.INTEGER, "180"),
    RAG_QA_TOP_K("RAG_QA_TOP_K", "RAG 质量评测召回 Top-K", SettingValueKind.INTEGER, "10"),
    RAG_QA_RETENTION_CRON(
            "RAG_QA_RETENTION_CRON",
            "RAG 质量评测历史清理 Cron（6 段，Asia/Shanghai）",
            SettingValueKind.STRING,
            "0 45 4 * * *"),
    RAG_SCHEDULED_TASKS_ENABLED(
            "RAG_SCHEDULED_TASKS_ENABLED", "租户通用定时任务 Poller 开关", SettingValueKind.BOOLEAN, "true"),
    RAG_SCHEDULED_TASKS_POLL_CRON(
            "RAG_SCHEDULED_TASKS_POLL_CRON",
            "租户通用定时任务扫描 Cron（6 段，Asia/Shanghai）",
            SettingValueKind.STRING,
            "0 * * * * *"),
    RAG_SCHEDULED_TASKS_POLLER_LOCK_TTL_SECONDS(
            "RAG_SCHEDULED_TASKS_POLLER_LOCK_TTL_SECONDS",
            "定时任务 Poller 分布式锁 TTL（秒）",
            SettingValueKind.INTEGER,
            "55"),
    RAG_SCHEDULED_TASKS_TASK_LOCK_TTL_SECONDS(
            "RAG_SCHEDULED_TASKS_TASK_LOCK_TTL_SECONDS",
            "单条定时任务执行锁 TTL（秒）",
            SettingValueKind.INTEGER,
            "300"),
    RAG_SITE_CRAWL_LOCK_TTL_SECONDS(
            "RAG_SITE_CRAWL_LOCK_TTL_SECONDS", "站点爬取执行锁 TTL（秒）", SettingValueKind.INTEGER, "21600"),
    RAG_SITE_CRAWL_QUEUE_RETENTION_CRON(
            "RAG_SITE_CRAWL_QUEUE_RETENTION_CRON",
            "爬取队列历史清理 Cron（6 段，Asia/Shanghai）",
            SettingValueKind.STRING,
            "0 30 3 * * *"),
    ADMIN_BRAND_LOGO_STORAGE_DIR(
            "ADMIN_BRAND_LOGO_STORAGE_DIR",
            "管理端租户 LOGO 本地上传根目录；空则使用 {user.dir}/var/admin-brand-logos",
            SettingValueKind.STRING,
            ""),
    CHAT_ATTACHMENT_BIN_DIR(
            "CHAT_ATTACHMENT_BIN_DIR",
            "会话附件原始文件落盘根目录；空则使用 {user.dir}/data/chat-attachment-bin",
            SettingValueKind.STRING,
            ""),
    CHAT_ATTACHMENT_VISION_OCR_FALLBACK(
            "CHAT_ATTACHMENT_VISION_OCR_FALLBACK",
            "图片附件本机 OCR 不足时是否回退租户视觉大模型",
            SettingValueKind.BOOLEAN,
            "true"),
    DOCUMENT_LOCAL_OCR_ENABLED(
            "DOCUMENT_LOCAL_OCR_ENABLED", "本机 Tesseract 图片 OCR 开关", SettingValueKind.BOOLEAN, "true"),
    DOCUMENT_LOCAL_OCR_LANGUAGES(
            "DOCUMENT_LOCAL_OCR_LANGUAGES",
            "Tesseract 语言包（如 chi_sim+eng）",
            SettingValueKind.STRING,
            "chi_sim+eng"),
    DOCUMENT_LOCAL_OCR_RAPID_ENABLED(
            "DOCUMENT_LOCAL_OCR_RAPID_ENABLED", "本机 RapidOCR（ONNX）开关", SettingValueKind.BOOLEAN, "true"),
    DOCUMENT_LOCAL_OCR_RAPID_MODEL(
            "DOCUMENT_LOCAL_OCR_RAPID_MODEL",
            "RapidOCR 模型（如 ONNX_PPOCR_V3）",
            SettingValueKind.STRING,
            "ONNX_PPOCR_V3"),
    RAG_RETRIEVAL_MODE(
            "RAG_RETRIEVAL_MODE",
            "进程默认 RAG 检索模式（milvus / milvus_es_hybrid；租户可覆盖）",
            SettingValueKind.STRING,
            "milvus_es_hybrid"),
    RAG_ES_MIN_SCORE(
            "RAG_ES_MIN_SCORE",
            "混合检索 ES 分支 BM25 最低分（低于此分的命中丢弃）",
            SettingValueKind.STRING,
            "1.0");

    @EnumValue
    private final String storage;

    private final String descriptionZh;
    private final SettingValueKind valueKind;
    private final String defaultValueText;

    public static Optional<PlatformSettingKey> fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = raw.trim();
        return Arrays.stream(values()).filter(k -> k.storage.equalsIgnoreCase(t)).findFirst();
    }

    public enum SettingValueKind {
        BOOLEAN,
        INTEGER,
        STRING
    }
}
