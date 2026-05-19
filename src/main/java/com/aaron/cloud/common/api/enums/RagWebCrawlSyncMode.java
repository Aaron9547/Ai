package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 网页爬取同步模式（固定枚举，前端下拉只读展示；扩展时在此增项即可）。 */
@Getter
@RequiredArgsConstructor
public enum RagWebCrawlSyncMode {
    INCREMENTAL("INCREMENTAL", "增量（仅新增 URL）"),
    FULL("FULL", "全量（先删本任务历史文档再重爬）"),
    FIRST_FULL_THEN_INCREMENTAL("FIRST_FULL_THEN_INCREMENTAL", "首次全量，后续增量"),
    ALWAYS_FULL("ALWAYS_FULL", "每次全量");

    @EnumValue
    private final String code;

    private final String label;

    public static RagWebCrawlSyncMode fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        String n = raw.trim().toUpperCase();
        for (RagWebCrawlSyncMode m : values()) {
            if (m.code.equals(n)) {
                return m;
            }
        }
        throw new IllegalArgumentException("unknown sync mode: " + raw);
    }

    public static List<Map<String, String>> metaList() {
        return Arrays.stream(values())
                .map(m -> Map.of("code", m.code, "label", m.label))
                .collect(Collectors.toList());
    }
}
