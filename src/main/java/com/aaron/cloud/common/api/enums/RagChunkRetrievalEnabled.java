package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 分片是否参与检索（词法/Milvus 等路径应尊重此标记）。 */
@Getter
@RequiredArgsConstructor
public enum RagChunkRetrievalEnabled {
    ENABLED(1),
    DISABLED(0);

    @EnumValue
    private final int code;

    public String getApiCode() {
        return name();
    }

    public static RagChunkRetrievalEnabled fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return ENABLED;
        }
        String s = raw.trim();
        for (RagChunkRetrievalEnabled v : values()) {
            if (v.name().equalsIgnoreCase(s) || String.valueOf(v.code).equals(s)) {
                return v;
            }
        }
        return ENABLED;
    }
}
