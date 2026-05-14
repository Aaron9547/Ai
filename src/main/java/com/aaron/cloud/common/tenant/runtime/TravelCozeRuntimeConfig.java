package com.aaron.cloud.common.tenant.runtime;

/**
 * 出差报销意图对接 Coze 工作流时的合并后参数（来自 {@code extra_config_json.handlerParams} 覆盖项 + {@code ten_runtime_setting} 回退）。
 *
 * <p>与 ly-ai-application {@code SystemConfigKey.TRAVEL_REIMBURSE_*} 对齐；域名可空，解析时回退官方默认。
 */
public record TravelCozeRuntimeConfig(
        String domain,
        String docApiKey,
        String docWorkflowId,
        String planApiKey,
        String planWorkflowId) {

    /** 四个工作流相关键均非空时，DOC/PLAN 两轮均走 Coze（否则走演示模拟）。 */
    public boolean allWorkflowKeysConfigured() {
        return notBlank(docApiKey)
                && notBlank(docWorkflowId)
                && notBlank(planApiKey)
                && notBlank(planWorkflowId);
    }

    public String resolvedDomain() {
        if (!notBlank(domain)) {
            return "https://api.coze.cn";
        }
        String d = domain.trim();
        return d.endsWith("/") ? d.substring(0, d.length() - 1) : d;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
