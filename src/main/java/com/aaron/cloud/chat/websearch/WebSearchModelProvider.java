package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.LlmWebSearchProvider;
import com.aaron.cloud.common.modelcfg.entity.SysLlmModel;

/** 可插拔：按 {@link LlmWebSearchProvider} 调用具体联网检索实现。 */
public interface WebSearchModelProvider {

    LlmWebSearchProvider supports();

    /**
     * @param row 租户 {@code llm_model} 行（须 {@code model_kind=WEB_SEARCH} 且已解密所需字段由调用方保证）
     * @param apiKeyPlaintext Ark API Key 明文
     * @param userQueryPlaintext 用户问题（可与附件拼接后的文本）
     */
    WebSearchExecutionResult execute(SysLlmModel row, String apiKeyPlaintext, String userQueryPlaintext) throws Exception;
}
