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
    RAG_WEB_CRAWL_DISPATCH("RAG_WEB_CRAWL_DISPATCH", "知识库网页爬取调度"),
    CHAT_STARTER_DAILY_HOT("CHAT_STARTER_DAILY_HOT", "对话推荐问题·每日热点"),
    KNOWLEDGE_PLANET_WEEKLY_COMPUTE("KNOWLEDGE_PLANET_WEEKLY_COMPUTE", "知识星球·周一方案计算"),
    KNOWLEDGE_PLANET_WEEKLY_EMAIL("KNOWLEDGE_PLANET_WEEKLY_EMAIL", "知识星球·周一邮件推送");

    @EnumValue
    private final String code;

    private final String label;

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

    public static List<Map<String, Object>> metaList() {
        return Arrays.stream(values())
                .map(e -> Map.<String, Object>of("code", e.code, "label", e.label))
                .collect(Collectors.toList());
    }
}
