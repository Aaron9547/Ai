package com.aaron.cloud.common.profile.memory;

import com.aaron.cloud.common.config.properties.AiMemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 未启用时，从 Redis List 拉取记忆抽象刷新任务（与 {@link MemoryAbstractAsyncPublisher} 共用 key）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnBean(StringRedisTemplate.class)
public class MemoryAbstractRedisQueuePoller {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final UserMemoryAbstractLlmWorker userMemoryAbstractLlmWorker;
    private final AiMemoryProperties aiMemoryProperties;

    @Scheduled(fixedDelayString = "${ai.memory.abstract-redis-poll-ms:400}")
    public void drain() {
        String key = aiMemoryProperties.getAbstractRedisQueueKey();
        for (int i = 0; i < 80; i++) {
            String json = redis.opsForList().leftPop(key, Duration.ofMillis(40));
            if (json == null) {
                break;
            }
            try {
                MemoryAbstractRefreshMessage m = objectMapper.readValue(json, MemoryAbstractRefreshMessage.class);
                userMemoryAbstractLlmWorker.runRefresh(m);
            } catch (Exception e) {
                log.error("memory abstract redis queue item failed, requeue tail json={}", json, e);
                redis.opsForList().rightPush(key, json);
                break;
            }
        }
    }
}
