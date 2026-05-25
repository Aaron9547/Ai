package com.aaron.cloud.common.api.enums.rag;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 知识库文档分片策略；入库与重索引时从 {@code rag_knowledge_base.default_chunk_strategy} 读取。
 * 可扩展：新增枚举值须同步 DDL 注释与前端选项。
 */
@Getter
@RequiredArgsConstructor
public enum RagChunkStrategy {
    /** 整篇作为单块（仍写入向量占位时可复用）。 */
    NONE(0),
    /** 按近似固定字符长度切分。 */
    FIXED_CHAR(1),
    /** 以段落边界为主，再合并/二次切分（近似语义段落）。 */
    SEMANTIC(2),
    /** 固定窗口 + 重叠。 */
    SLIDING_WINDOW(3),
    /** 子母分片：母块存上下文（不参与检索），子块 embed 检索。 */
    PARENT_CHILD(4);

    @EnumValue private final int code;

    public static RagChunkStrategy fromCode(int code) {
        for (RagChunkStrategy s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        if (code == 99) {
            return FIXED_CHAR;
        }
        return FIXED_CHAR;
    }
}
