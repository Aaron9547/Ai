package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 定时任务调度周期（固定枚举，不可在管理端自定义新增）。
 *
 * <p>{@code intervalDays} 为 0 表示仅手动触发。
 */
@Getter
@RequiredArgsConstructor
public enum ScheduledTaskIntervalPreset {
    MANUAL("MANUAL", "仅手动", 0),
    DAILY("DAILY", "每天", 1),
    EVERY_2_DAYS("EVERY_2_DAYS", "每 2 天", 2),
    WEEKLY("WEEKLY", "每周", 7),
    BIWEEKLY("BIWEEKLY", "每 14 天", 14),
    MONTHLY("MONTHLY", "每 30 天", 30);

    @EnumValue
    private final String code;

    private final String label;

    /** 距上次执行至少间隔的天数；0 表示不自动调度。 */
    private final int intervalDays;

    public static ScheduledTaskIntervalPreset fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MANUAL;
        }
        String n = raw.trim().toUpperCase();
        for (ScheduledTaskIntervalPreset p : values()) {
            if (p.code.equals(n)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown schedule preset: " + raw);
    }

    public static List<Map<String, Object>> metaList() {
        return Arrays.stream(values())
                .map(
                        p ->
                                Map.<String, Object>of(
                                        "code", p.code,
                                        "label", p.label,
                                        "intervalDays", p.intervalDays))
                .collect(Collectors.toList());
    }
}
