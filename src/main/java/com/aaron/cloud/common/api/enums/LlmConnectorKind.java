package com.aaron.cloud.common.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 管理端可扩展的「对接协议」枚举：与 {@code llm_model} 编排形态对应；当前仅 OpenAI 兼容 JSON。后续新增非 OpenAI 形态时在此加枚举并在
 * 元数据/持久化中挂接，管理端列表与表单由 {@code GET /api/v1/admin/llm-models/meta} 下发字段策略即可扩展。
 */
@Getter
@RequiredArgsConstructor
public enum LlmConnectorKind {
    OPENAI_COMPATIBLE_JSON("OPENAI_COMPATIBLE_JSON");

    private final String code;

    public static LlmConnectorKind fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPENAI_COMPATIBLE_JSON;
        }
        String s = raw.trim();
        for (LlmConnectorKind k : values()) {
            if (k.code.equalsIgnoreCase(s) || k.name().equalsIgnoreCase(s)) {
                return k;
            }
        }
        return OPENAI_COMPATIBLE_JSON;
    }
}
