package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.modelcfg.quota.LlmUsageDigestMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${ai.rocketmq.llm-usage-topic:ai-llm-usage}",
        consumerGroup = "${ai.rocketmq.llm-usage-consumer-group:ai-llm-usage-consumer}")
public class LlmUsageRocketMqListener implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final LlmUsagePersistenceService persistenceService;

    @Override
    public void onMessage(String message) {
        try {
            LlmUsageDigestMessage m = objectMapper.readValue(message, LlmUsageDigestMessage.class);
            persistenceService.persist(m);
        } catch (Exception e) {
            log.error("llm usage rocket consume failed payload={}", message, e);
        }
    }
}
