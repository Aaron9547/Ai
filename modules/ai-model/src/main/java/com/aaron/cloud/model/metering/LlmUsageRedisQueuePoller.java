package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.modelcfg.quota.LlmUsageDigestMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 未启用时，从 Redis List 拉取用量消息并落库（Redis 作轻量队列）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnBean(StringRedisTemplate.class)
public class LlmUsageRedisQueuePoller {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final LlmUsagePersistenceService persistenceService;

    @Value("${ai.llm-usage.redis-queue-key:ai:queue:llm-usage}")
    private String redisQueueKey;

    @Scheduled(fixedDelayString = "${ai.llm-usage.redis-poll-ms}")
    public void drain() {
        for (int i = 0; i < 200; i++) {
            String json = redis.opsForList().leftPop(redisQueueKey, Duration.ofMillis(50));
            if (json == null) {
                break;
            }
            try {
                LlmUsageDigestMessage m = objectMapper.readValue(json, LlmUsageDigestMessage.class);
                persistenceService.persist(m);
            } catch (Exception e) {
                log.error("llm usage redis queue item failed, requeue tail json={}", json, e);
                redis.opsForList().rightPush(redisQueueKey, json);
                break;
            }
        }
    }
}
