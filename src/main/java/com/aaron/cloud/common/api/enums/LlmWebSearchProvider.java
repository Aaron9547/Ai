package com.aaron.cloud.common.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 联网搜索模型（{@link LlmModelKind#WEB_SEARCH}）的对接实现；WEB_SEARCH 行在 {@code llm_model.integration_backend} 存本枚举 {@link #code}。
 *
 * <p>{@link #VOLCENGINE_ARK_BOT}：对 {@link com.aaron.cloud.common.modelcfg.entity.SysLlmModel#getOpenaiBaseUrl()} 规范化后 POST Ark
 * Bot Chat Completions；{@code openai_model_id} 存 Bot ID；{@code api_key_cipher} 存 Ark API Key。
 */
@Getter
@RequiredArgsConstructor
public enum LlmWebSearchProvider {
    VOLCENGINE_ARK_BOT("VOLCENGINE_ARK_BOT");

    @EnumValue private final String code;

    public static LlmWebSearchProvider fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (LlmWebSearchProvider p : values()) {
            if (p.code.equalsIgnoreCase(s) || p.name().equalsIgnoreCase(s)) {
                return p;
            }
        }
        return null;
    }
}
