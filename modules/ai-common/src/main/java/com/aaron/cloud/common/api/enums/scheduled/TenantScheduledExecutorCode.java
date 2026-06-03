package com.aaron.cloud.common.api.enums.scheduled;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 定时任务执行器（固定枚举；调度层只登记 code，业务逻辑在执行器内）。 */
@Getter
@RequiredArgsConstructor
public enum TenantScheduledExecutorCode {
    RAG_WEB_CRAWL_DISPATCH(
            "RAG_WEB_CRAWL_DISPATCH", "知识库网页爬取调度", TenantScheduledTaskCategory.TENANT_CRON),
    CHAT_STARTER_DAILY_HOT(
            "CHAT_STARTER_DAILY_HOT", "对话推荐问题·每日热点", TenantScheduledTaskCategory.TENANT_CRON),
    KNOWLEDGE_PLANET_WEEKLY_COMPUTE(
            "KNOWLEDGE_PLANET_WEEKLY_COMPUTE",
            "知识星球·周一方案计算",
            TenantScheduledTaskCategory.TENANT_CRON),
    KNOWLEDGE_PLANET_WEEKLY_EMAIL(
            "KNOWLEDGE_PLANET_WEEKLY_EMAIL",
            "知识星球·周一邮件推送",
            TenantScheduledTaskCategory.TENANT_CRON),
    CHAT_USER_REMINDER(
            "CHAT_USER_REMINDER", "对话·用户提醒邮件", TenantScheduledTaskCategory.CHAT_USER_REMINDER);

    @EnumValue
    private final String code;

    private final String label;

    private final TenantScheduledTaskCategory taskCategory;

    public static TenantScheduledExecutorCode fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("executorCode required");
        }
        String n = raw.trim().toUpperCase();
        for (TenantScheduledExecutorCode e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown scheduled executor: " + raw);
    }

    /** 未知 code 时回退为 code 本身，避免管理端展示空白。 */
    public static String labelOfCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return fromCode(raw).getLabel();
        } catch (IllegalArgumentException e) {
            return raw.trim();
        }
    }

    public static List<TenantScheduledExecutorCode> byTaskCategory(TenantScheduledTaskCategory category) {
        if (category == null) {
            return List.of(values());
        }
        return Arrays.stream(values()).filter(e -> e.taskCategory == category).toList();
    }

    public static List<Map<String, Object>> metaList() {
        return Arrays.stream(values())
                .map(
                        e ->
                                Map.<String, Object>of(
                                        "code",
                                        e.code,
                                        "label",
                                        e.label,
                                        "taskCategory",
                                        e.taskCategory.getCode()))
                .collect(Collectors.toList());
    }
}
