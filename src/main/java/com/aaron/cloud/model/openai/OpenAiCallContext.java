package com.aaron.cloud.model.openai;

/**
 * 一次 OpenAI 兼容流式调用的排障上下文（不落库、不写响应体；日志中便于对齐租户与模型行）。
 *
 * @param tenantId 租户
 * @param modelAlias 路由别名（如 QW）
 * @param llmModelId 表 {@code llm_model.id}，全局 yaml 回退可为 null
 * @param providerModelId 请求体中的 {@code model}（厂商侧 id）
 * @param configuredBaseUrl 管理端 / 配置中的基址（解析前）
 */
public record OpenAiCallContext(
        Long tenantId,
        String modelAlias,
        Long llmModelId,
        String providerModelId,
        String configuredBaseUrl) {

    public String toLogString() {
        String base = configuredBaseUrl == null ? "" : configuredBaseUrl.trim().replaceAll("/+$", "");
        return "tenantId="
                + tenantId
                + " modelAlias="
                + (modelAlias == null ? "" : modelAlias)
                + " llmModelId="
                + llmModelId
                + " providerModel="
                + (providerModelId == null ? "" : providerModelId)
                + " baseUrlCfg="
                + base;
    }
}
