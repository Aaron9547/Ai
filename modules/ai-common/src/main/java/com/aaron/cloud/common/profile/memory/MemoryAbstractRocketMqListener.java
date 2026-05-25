package com.aaron.cloud.common.profile.memory;

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
        topic = "${ai.rocketmq.memory-abstract-topic:ai-memory-abstract-refresh}",
        consumerGroup = "${ai.rocketmq.memory-abstract-consumer-group:ai-memory-abstract-consumer}")
public class MemoryAbstractRocketMqListener implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final UserMemoryAbstractLlmWorker userMemoryAbstractLlmWorker;

    @Override
    public void onMessage(String message) {
        try {
            MemoryAbstractRefreshMessage m = objectMapper.readValue(message, MemoryAbstractRefreshMessage.class);
            userMemoryAbstractLlmWorker.runRefresh(m);
        } catch (Exception e) {
            log.error("memory abstract rocket consume failed payload={}", message, e);
        }
    }
}
