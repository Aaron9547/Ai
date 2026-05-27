package com.aaron.cloud.common.api.enums.gateway;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理端菜单/接口能力码；与路由 meta、{@code lnk_*_admin_menu} 中存储的字符串一致。
 */
@Getter
@RequiredArgsConstructor
public enum AdminMenuCode {
    /** 管理端首页数据大屏（当前租户统计） */
    DASHBOARD("DASHBOARD"),
    USERS("USERS"),
    TENANTS("TENANTS"),
    ACCESS_LOGS("ACCESS_LOGS"),
    AUDIT_EVENTS("AUDIT_EVENTS"),
    METERING("METERING"),
    LLM_MODELS("LLM_MODELS"),
    /** LLM 提示词模板（prompt_template） */
    PROMPT_TEMPLATES("PROMPT_TEMPLATES"),
    MCP_SERVERS("MCP_SERVERS"),
    RAG_KBS("RAG_KBS"),
    /** 租户通用定时任务（网页爬取等；执行记录在入库任务） */
    SCHEDULED_TASKS("SCHEDULED_TASKS"),
    /** 消息通道、场景模板与发送记录 */
    MESSAGE_CENTER("MESSAGE_CENTER"),
    FILE_OBJECTS("FILE_OBJECTS"),
    NOTIFICATIONS("NOTIFICATIONS"),
    EVAL_RUNS("EVAL_RUNS"),
    /** 租户内用户画像与记忆向量化模型配置 */
    USER_PROFILES("USER_PROFILES"),
    /** 租户内对话日志、消息与用量只读查询 */
    CHAT("CHAT"),
    /** 对话意图与触发关键词配置 */
    CHAT_INTENTS("CHAT_INTENTS"),
    /** 租户级免重启系统参数（开放注册等） */
    SYSTEM_SETTINGS("SYSTEM_SETTINGS"),
    /** 管理端侧栏菜单项元数据（展示名、排序、路由提示等） */
    MENU_CATALOG("MENU_CATALOG"),
    /** 接口清单与按路径的限流策略 */
    GATEWAY_API("GATEWAY_API");

    private final String code;

    public static Set<AdminMenuCode> all() {
        return new LinkedHashSet<>(Arrays.asList(values()));
    }

    public static AdminMenuCode fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AdminMenuCode c : values()) {
            if (c.name().equalsIgnoreCase(raw.trim()) || c.code.equalsIgnoreCase(raw.trim())) {
                return c;
            }
        }
        return null;
    }
}
