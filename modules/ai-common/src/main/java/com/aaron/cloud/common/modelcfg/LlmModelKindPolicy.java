package com.aaron.cloud.common.modelcfg;

import com.aaron.cloud.common.api.enums.llm.LlmModelKind;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;

/** 对话流与 OpenAI Chat 流式路径允许的模型类型约束（与按模型行累计 token 用量无关）。 */
public final class LlmModelKindPolicy {

    private LlmModelKindPolicy() {}

    /** 仅 {@link LlmModelKind#LANGUAGE} 可绑定 C 端对话与流式补全。 */
    public static void assertLanguageModelForChatStream(SysLlmModel m) {
        LlmModelKind k = m.getModelKind() != null ? m.getModelKind() : LlmModelKind.LANGUAGE;
        if (k != LlmModelKind.LANGUAGE) {
            throw new IllegalArgumentException("仅语言模型可用于对话，当前类型：" + k.getCode());
        }
    }
}
