package com.aaron.cloud.common.outbound;

/** 出站调用分类，用于熔断命名与指标 tag。 */
public enum OutboundKind {
    /** 租户对话等经 {@code ModelInvokePort} 的流式补全（local 引擎内为 OpenAI 兼容 SSE）。 */
    LLM_STREAM,
    /** 联网搜索 Bot 等非流式上游。 */
    LLM_WEB_SEARCH,
    /** 向量嵌入直连 HTTP。 */
    LLM_EMBEDDING,
    /** 本地部署嵌入经 Feign。 */
    LLM_EMBEDDING_FEIGN,
    /** 意图 Coze 工作流 SSE。 */
    INTENT_COZE
}
