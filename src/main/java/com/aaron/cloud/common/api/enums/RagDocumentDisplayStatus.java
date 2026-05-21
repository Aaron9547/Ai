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
    /** 正文已落库，分片/向量化尚未完成。 */
    PARSING("PARSING"),
    /** 分片中或正在调用嵌入模型写入向量库（及混合模式下的 ES）。 */
    EMBEDDING("EMBEDDING"),
    /** 分片与向量索引均完成，可参与检索。 */
    PUBLISHED("PUBLISHED"),
    /** 解析或正文质量校验失败。 */
    PARSE_FAILED("PARSE_FAILED"),
    /** 分片、嵌入或 Milvus/ES 索引失败。 */
    INDEX_FAILED("INDEX_FAILED");

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
