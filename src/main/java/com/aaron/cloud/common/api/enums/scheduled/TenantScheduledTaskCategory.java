package com.aaron.cloud.common.api.enums.scheduled;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 管理端定时任务 Tab 分类：租户级 Cron 注册 vs 对话意图创建的用户提醒。 */
@Getter
@RequiredArgsConstructor
public enum TenantScheduledTaskCategory {
    TENANT_CRON("TENANT_CRON", "租户定时任务"),
    CHAT_USER_REMINDER("CHAT_USER_REMINDER", "对话用户提醒");

    private final String code;
    private final String label;

    public static TenantScheduledTaskCategory fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("taskCategory required");
        }
        String n = raw.trim().toUpperCase();
        for (TenantScheduledTaskCategory e : values()) {
            if (e.code.equals(n)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown scheduled task category: " + raw);
    }

    public static List<Map<String, Object>> metaList() {
        return Arrays.stream(values())
                .map(e -> Map.<String, Object>of("code", e.code, "label", e.label))
                .collect(Collectors.toList());
    }
}
