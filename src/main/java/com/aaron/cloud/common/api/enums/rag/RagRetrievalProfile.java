package com.aaron.cloud.common.api.enums.rag;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 对话 RAG 检索分流：简单问句走向量快通道，复杂问句走混合 + LTR。 */
@Getter
@RequiredArgsConstructor
public enum RagRetrievalProfile {
    SIMPLE_VECTOR("simple_vector"),
    COMPLEX_HYBRID("complex_hybrid");

    @EnumValue private final String code;

    /** null 或未识别时默认复杂混合路径。 */
    public static RagRetrievalProfile effective(RagRetrievalProfile profile) {
        return profile == null ? COMPLEX_HYBRID : profile;
    }

    public static RagRetrievalProfile fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMPLEX_HYBRID;
        }
        String t = raw.trim().toLowerCase();
        for (RagRetrievalProfile p : values()) {
            if (p.code.equals(t) || p.name().equalsIgnoreCase(t)) {
                return p;
            }
        }
        return COMPLEX_HYBRID;
    }
}
