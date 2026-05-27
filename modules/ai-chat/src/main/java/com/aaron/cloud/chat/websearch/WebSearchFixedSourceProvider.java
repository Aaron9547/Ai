package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;

/** 内置固定联网检索源（不依赖 {@code llm_model} 行）。 */
public interface WebSearchFixedSourceProvider {

    WebSearchFixedSource supports();

    WebSearchExecutionResult execute(long tenantId, String userQueryPlaintext) throws Exception;
}
