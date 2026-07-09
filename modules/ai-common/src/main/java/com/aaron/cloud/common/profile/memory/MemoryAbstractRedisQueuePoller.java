package com.aaron.cloud.common.profile.memory;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RocketMQ 未启用时，从 Redis List 拉取记忆抽象刷新任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnBean(StringRedisTemplate.class)
public class MemoryAbstractRedisQueuePoller {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final UserMemoryAbstractLlmWorker userMemoryAbstractLlmWorker;
    private final PlatformSettingApplicationService platformSettings;

    private volatile long lastDrainAtMs;

    @Scheduled(fixedDelay = 50)
    public void tick() {
        long interval = platformSettings.getLong(PlatformSettingKey.MEMORY_ABSTRACT_REDIS_POLL_MS);
        long now = System.currentTimeMillis();
        if (now - lastDrainAtMs < interval) {
            return;
        }
        lastDrainAtMs = now;
        drain();
    }

    private void drain() {
        String key = AiInternalResourceNames.RedisQueues.MEMORY_ABSTRACT;
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
