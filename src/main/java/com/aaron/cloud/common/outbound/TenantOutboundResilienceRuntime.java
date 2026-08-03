package com.aaron.cloud.common.outbound;

import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.config.properties.AiOutboundResilienceProperties;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 将进程级 {@code ai.outbound} 与租户 {@link TenantRuntimeSettingKey#OUTBOUND_RESILIENCE_JSON} 深度合并；
 * {@code circuitBreaker} 子树始终取自进程基线（租户 JSON 中该键忽略）。
 */
@Slf4j
@Service
public class TenantOutboundResilienceRuntime {

    private final AiOutboundResilienceProperties baselineProps;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ObjectMapper mergeMapper;

    public TenantOutboundResilienceRuntime(
            AiOutboundResilienceProperties baselineProps,
            TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService,
            ObjectMapper objectMapper) {
        this.baselineProps = baselineProps;
        this.tenantRuntimeSettingApplicationService = tenantRuntimeSettingApplicationService;
        this.mergeMapper = objectMapper.copy();
        this.mergeMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 返回<strong>新实例</strong>（勿缓存后长期持有）；{@code tenantId <= 0} 时等价于进程基线快照。
     */
    public AiOutboundResilienceProperties effective(long tenantId) {
        if (tenantId <= 0) {
            return cloneBaseline();
        }
        String overlayText =
                tenantRuntimeSettingApplicationService.getEffectiveValueText(
                        tenantId, TenantRuntimeSettingKey.OUTBOUND_RESILIENCE_JSON);
        try {
            JsonNode base = mergeMapper.valueToTree(baselineProps);
            JsonNode overlayNode;
            try {
                overlayNode = mergeMapper.readTree(overlayText == null || overlayText.isBlank() ? "{}" : overlayText);
            } catch (Exception e) {
                log.warn("tenant outbound JSON parse failed tenantId={}", tenantId, e);
                overlayNode = mergeMapper.createObjectNode();
            }
            if (!overlayNode.isObject()) {
                log.warn("tenant outbound JSON is not object tenantId={}", tenantId);
                overlayNode = mergeMapper.createObjectNode();
            }
            ObjectNode overlayObj = (ObjectNode) overlayNode;
            overlayObj.remove("circuitBreaker");
            JsonNode merged = deepMergeObjects(base, overlayObj);
            AiOutboundResilienceProperties out =
                    mergeMapper.treeToValue(merged, AiOutboundResilienceProperties.class);
            copyCircuitBreakerFromBaseline(out);
            return out;
        } catch (Exception e) {
            log.warn("tenant outbound merge failed tenantId={}, fallback baseline", tenantId, e);
            return cloneBaseline();
        }
    }

    private AiOutboundResilienceProperties cloneBaseline() {
        try {
            JsonNode n = mergeMapper.valueToTree(baselineProps);
            AiOutboundResilienceProperties c = mergeMapper.treeToValue(n, AiOutboundResilienceProperties.class);
            copyCircuitBreakerFromBaseline(c);
            return c;
        } catch (Exception e) {
            log.error("outbound baseline clone failed", e);
            return baselineProps;
        }
    }

    private void copyCircuitBreakerFromBaseline(AiOutboundResilienceProperties target) {
        copyCircuitBreakerProps(baselineProps.getCircuitBreaker(), target.getCircuitBreaker());
    }

    private static JsonNode deepMergeObjects(JsonNode baseNode, ObjectNode overlay) {
        if (baseNode == null || !baseNode.isObject()) {
            return overlay == null ? JsonNodeFactory.instance.objectNode() : overlay.deepCopy();
        }
        ObjectNode out = (ObjectNode) baseNode.deepCopy();
        if (overlay == null) {
            return out;
        }
        overlay.fields()
                .forEachRemaining(
                        e -> {
                            String field = e.getKey();
                            JsonNode ov = e.getValue();
                            if (!out.has(field)) {
                                log.debug("tenant outbound overlay ignores unknown key: {}", field);
                                return;
                            }
                            JsonNode bv = out.get(field);
                            if (ov != null && ov.isObject() && bv != null && bv.isObject()) {
                                out.set(field, deepMergeObjects(bv, (ObjectNode) ov));
                            } else {
                                out.set(field, ov == null ? null : ov.deepCopy());
                            }
                        });
        return out;
    }

    private static void copyCircuitBreakerProps(
            AiOutboundResilienceProperties.CircuitBreakerProps from,
            AiOutboundResilienceProperties.CircuitBreakerProps to) {
        if (from == null || to == null) {
            return;
        }
        to.setEnabled(from.isEnabled());
        to.setSlidingWindowSize(from.getSlidingWindowSize());
        to.setMinimumNumberOfCalls(from.getMinimumNumberOfCalls());
        to.setFailureRateThreshold(from.getFailureRateThreshold());
        to.setWaitDurationInOpenState(from.getWaitDurationInOpenState());
        to.setSlowCallDurationThreshold(from.getSlowCallDurationThreshold());
        to.setSlowCallRateThreshold(from.getSlowCallRateThreshold());
        to.setPermittedNumberOfCallsInHalfOpenState(from.getPermittedNumberOfCallsInHalfOpenState());
    }
}
