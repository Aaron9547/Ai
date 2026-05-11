package com.aaron.cloud.common.api.enums;

/** C 端用户输入被同步护栏拦截时的原因（与 SSE {@code inputBlocked.reason} 及落库 meta 对齐）。 */
public enum ChatInputBlockReason {
    INVALID_INPUT,
    SENSITIVE_CONTENT,
    PROMPT_INJECTION_ATTEMPT,
    GUARDRAIL_POLICY
}
