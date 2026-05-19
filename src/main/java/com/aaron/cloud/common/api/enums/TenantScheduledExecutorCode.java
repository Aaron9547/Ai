package com.aaron.cloud.common.api.enums;

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
    RAG_WEB_CRAWL_DISPATCH("RAG_WEB_CRAWL_DISPATCH", "知识库网页爬取调度");

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

    public static List<Map<String, Object>> metaList() {
        return Arrays.stream(values())
                .map(e -> Map.<String, Object>of("code", e.code, "label", e.label))
                .collect(Collectors.toList());
    }
}
