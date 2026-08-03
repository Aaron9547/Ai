package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.config.properties.RocketMqAppProperties;
import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.aaron.cloud.common.modelcfg.quota.LlmUsageDigestMessage;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RocketMQ 未启用时，从 Redis List 拉取用量消息并落库。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnBean(StringRedisTemplate.class)
public class LlmUsageRedisQueuePoller {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final LlmUsagePersistenceService persistenceService;
    private final PlatformSettingApplicationService platformSettings;

    private volatile long lastDrainAtMs;

    @Scheduled(fixedDelay = 50)
    public void tick() {
        long interval = platformSettings.getLong(PlatformSettingKey.LLM_USAGE_REDIS_POLL_MS);
        long now = System.currentTimeMillis();
        if (now - lastDrainAtMs < interval) {
            return;
        }
        lastDrainAtMs = now;
        drain();
    }

    private void drain() {
        String redisQueueKey = AiInternalResourceNames.RedisQueues.LLM_USAGE;
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
