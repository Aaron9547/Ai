package com.aaron.cloud.chat.intent;

import com.aaron.cloud.common.tenant.runtime.TravelCozeRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 出差报销意图在 {@code extra_config_json.handlerParams} 下的 Coze 配置；由管理端「意图识别」维护。
 *
 * <p>键名与管理端 schema 一致：{@code cozeDomain}、{@code docCozeApiKey}、{@code docWorkflowId}、{@code planCozeApiKey}、{@code planWorkflowId}。
 */
public record TravelHandlerParams(
        String cozeDomain, String docCozeApiKey, String docWorkflowId, String planCozeApiKey, String planWorkflowId) {

    private static final Logger LOG = LoggerFactory.getLogger(TravelHandlerParams.class);

    public static TravelHandlerParams empty() {
        return new TravelHandlerParams(null, null, null, null, null);
    }

    public static TravelHandlerParams parse(String extraConfigJson, ObjectMapper objectMapper) {
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return empty();
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            JsonNode hp = root.path("handlerParams");
            if (!hp.isObject()) {
                return empty();
            }
            return new TravelHandlerParams(
                    text(hp, "cozeDomain"),
                    text(hp, "docCozeApiKey"),
                    text(hp, "docWorkflowId"),
                    text(hp, "planCozeApiKey"),
                    text(hp, "planWorkflowId"));
        } catch (Exception e) {
            LOG.warn("TravelHandlerParams parse failed: {}", e.toString());
            return empty();
        }
    }

    private static String text(JsonNode parent, String field) {
        if (!parent.has(field) || parent.get(field).isNull()) {
            return null;
        }
        String t = parent.get(field).asText("").strip();
        return t.isEmpty() ? null : t;
    }

    public TravelCozeRuntimeConfig toRuntimeConfig() {
        return new TravelCozeRuntimeConfig(cozeDomain, docCozeApiKey, docWorkflowId, planCozeApiKey, planWorkflowId);
    }
}
