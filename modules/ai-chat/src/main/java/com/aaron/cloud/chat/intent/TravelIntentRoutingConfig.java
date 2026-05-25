package com.aaron.cloud.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从意图 {@code extra_config_json} 解析的多轮路由参数（与处理器 {@code TRAVEL_REIMBURSEMENT} 对齐）。
 *
 * <p>JSON 约定（可只写子集，未出现字段用默认值）：
 *
 * <pre>
 * {
 *   "travelRouting": {
 *     "allowDocAdvanceWithAttachmentOnly": true,
 *     "sessionExpiredUserHint": "可选；会话失效时 SSE 提示文案",
 *     "sessionTtlMs": 600000
 *   },
 *   "handlerParams": { "...": "与处理器插件 schema 对齐；与 travelRouting 并列、互不覆盖" }
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code allowDocAdvanceWithAttachmentOnly=true}（默认）：DOC 等待材料阶段，用户<strong>仅带附件</strong>也可命中意图并推进（兼容单轮「附件即入口」）。
 *   <li>{@code false}：DOC 阶段须<strong>同轮含首轮触发短语</strong>才会命中（先匹配触发词再处理附件；仅附件走大模型主链，满足「第二轮须触发语+附件」类运营策略）。
 * </ul>
 */
public record TravelIntentRoutingConfig(
        boolean allowDocAdvanceWithAttachmentOnly, String sessionExpiredUserHint, long sessionTtlMs) {

    private static final Logger LOG = LoggerFactory.getLogger(TravelIntentRoutingConfig.class);

    public static TravelIntentRoutingConfig defaults() {
        return new TravelIntentRoutingConfig(true, null, 600_000L);
    }

    public static TravelIntentRoutingConfig parse(String extraConfigJson, ObjectMapper objectMapper) {
        if (extraConfigJson == null || extraConfigJson.isBlank()) {
            return defaults();
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfigJson);
            JsonNode tr = root.path("travelRouting");
            if (!tr.isObject()) {
                return defaults();
            }
            boolean allowAttach =
                    !tr.has("allowDocAdvanceWithAttachmentOnly")
                            || tr.get("allowDocAdvanceWithAttachmentOnly").asBoolean(true);
            String hint = null;
            if (tr.has("sessionExpiredUserHint") && tr.get("sessionExpiredUserHint").isTextual()) {
                String t = tr.get("sessionExpiredUserHint").asText().strip();
                hint = t.isEmpty() ? null : t;
            }
            long ttl = 600_000L;
            if (tr.has("sessionTtlMs") && tr.get("sessionTtlMs").isIntegralNumber()) {
                ttl = Math.max(30_000L, tr.get("sessionTtlMs").asLong());
            }
            return new TravelIntentRoutingConfig(allowAttach, hint, ttl);
        } catch (Exception e) {
            LOG.warn("travelRouting parse failed, using defaults: {}", e.toString());
            return defaults();
        }
    }

    public String resolvedSessionExpiredHint() {
        if (sessionExpiredUserHint != null && !sessionExpiredUserHint.isBlank()) {
            return sessionExpiredUserHint;
        }
        return "当前意图会话已结束或过期，请再次发送触发关键词开始。";
    }
}
