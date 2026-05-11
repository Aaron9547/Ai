package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 知识库文档在管理端的展示/生命周期状态（与 {@code source_type} 独立，用于列表筛选与操作差异化）。
 */
@Getter
@RequiredArgsConstructor
public enum RagDocumentDisplayStatus {
    /** 已入库且可检索（默认）。 */
    PUBLISHED("PUBLISHED"),
    /** 异步解析或流水线处理中。 */
    PARSING("PARSING"),
    /** 解析或入库失败，可重试。 */
    PARSE_FAILED("PARSE_FAILED");

    @EnumValue private final String code;

    public static RagDocumentDisplayStatus fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return PUBLISHED;
        }
        String s = raw.trim();
        for (RagDocumentDisplayStatus v : values()) {
            if (v.code.equalsIgnoreCase(s) || v.name().equalsIgnoreCase(s)) {
                return v;
            }
        }
        return PUBLISHED;
    }
}
