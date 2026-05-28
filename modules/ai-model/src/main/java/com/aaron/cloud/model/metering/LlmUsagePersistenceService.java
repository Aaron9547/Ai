package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.api.enums.metering.MeteringMeterType;
import com.aaron.cloud.common.metering.MeteringUsageEventRepository;
import com.aaron.cloud.common.metering.entity.MeteringUsageEvent;
import com.aaron.cloud.common.modelcfg.SysLlmModelRepository;
import com.aaron.cloud.common.modelcfg.quota.LlmUsageDigestMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 异步消费：累加 DB tokens_used + 写入计量流水 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmUsagePersistenceService {

    private final SysLlmModelRepository llmModelRepository;
    private final MeteringUsageEventRepository meteringUsageEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persist(LlmUsageDigestMessage m) {
        if (m.totalTokens() <= 0) {
            return;
        }
        llmModelRepository.appendTokensUsed(m.tenantId(), m.llmModelId(), m.totalTokens());
        try {
            var ev = new MeteringUsageEvent();
            ev.setTenantId(m.tenantId());
            ev.setUserId(m.userId());
            ev.setDeviceId(m.deviceId());
            ev.setMeterType(MeteringMeterType.resolveForPersist(m.meterTypeCode()).getCode());
            ev.setQuantity(BigDecimal.valueOf(m.totalTokens()));
            ev.setUnit("token");
            ObjectNode ref = objectMapper.createObjectNode();
            ref.put("modelAlias", m.modelAlias());
            ref.put("llmModelId", m.llmModelId());
            if (m.modelKindCode() != null && !m.modelKindCode().isBlank()) {
                ref.put("modelKind", m.modelKindCode());
            }
            ref.put("promptTokens", m.promptTokens());
            ref.put("completionTokens", m.completionTokens());
            ref.put("totalTokens", m.totalTokens());
            ref.put("conversationId", m.conversationId());
            if (m.durationMs() != null && m.durationMs() > 0) {
                ref.put("durationMs", m.durationMs());
            }
            if (m.usageSceneCode() != null && !m.usageSceneCode().isBlank()) {
                ref.put("usageScene", m.usageSceneCode());
            }
            ev.setRefJson(objectMapper.writeValueAsString(ref));
            meteringUsageEventRepository.insert(ev);
        } catch (Exception ex) {
            log.warn("metering insert failed tenant={} model={}", m.tenantId(), m.llmModelId(), ex);
        }
    }
}
