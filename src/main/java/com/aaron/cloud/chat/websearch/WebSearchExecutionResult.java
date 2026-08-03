package com.aaron.cloud.chat.websearch;

/** Provider 执行结果：聚合文案 + 可选原始 JSON（用于 usage 计量）。 */
public record WebSearchExecutionResult(WebGroundingBundle bundle, String rawResponseBody) {}
