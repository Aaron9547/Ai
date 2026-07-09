package com.aaron.cloud.model.metering;

import com.aaron.cloud.common.config.properties.RocketMqAppProperties;
import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.aaron.cloud.common.modelcfg.quota.LlmUsageDigestMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用量异步落库投递：优先 RocketMQ；失败或未启用时写入 Redis List，由本机调度消费；再无 Redis 则同步落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmUsageAsyncPublisher {

    private final ObjectMapper objectMapper;
    private final LlmUsagePersistenceService persistenceService;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final RocketMqAppProperties rocketMqApp;

    public void publish(LlmUsageDigestMessage message) {
        if (message.totalTokens() <= 0) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            if (rocketMqApp.isEnabled()) {
                var rocket = rocketMQTemplate.getIfAvailable();
                if (rocket != null) {
                    try {
                        rocket.syncSend(rocketMqApp.getLlmUsageTopic(), json);
                        return;
                    } catch (Exception e) {
                        log.warn("rocket llm-usage send failed, sync persist (redis queue poller is off when MQ on)", e);
                        persistenceService.persist(message);
                        return;
                    }
                }
            }
            var redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                redis.opsForList().rightPush(AiInternalResourceNames.RedisQueues.LLM_USAGE, json);
                return;
            }
            persistenceService.persist(message);
        } catch (Exception e) {
            log.error("llm usage publish failed, sync persist", e);
            try {
                persistenceService.persist(message);
            } catch (Exception ex) {
                log.error("llm usage sync persist failed", ex);
            }
        }
    }
}
