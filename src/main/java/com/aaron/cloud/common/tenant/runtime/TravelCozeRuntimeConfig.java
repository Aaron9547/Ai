package com.aaron.cloud.common.tenant.runtime;

/**
 * 出差报销意图对接 Coze 工作流时的参数（来自意图 {@code extra_config_json.handlerParams}）。
 *
 * <p>域名可空，解析时回退官方默认 {@code https://api.coze.cn}。
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
