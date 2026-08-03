package com.aaron.cloud.common.profile.memory;

import com.aaron.cloud.common.config.properties.RocketMqAppProperties;
import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 记忆抽象层刷新：优先 RocketMQ；未启用或发送失败时写入 Redis List；再无 Redis 且开启同步兜底时直接执行 Worker。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAbstractAsyncPublisher {

    private final ObjectMapper objectMapper;
    private final RocketMqAppProperties rocketMqApp;
    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplate;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
    private final UserMemoryAbstractLlmWorker userMemoryAbstractLlmWorker;

    public void publish(MemoryAbstractRefreshMessage message) {
        if (message == null || message.getSubjectKey() == null || message.getSubjectKey().isBlank()) {
            return;
        }
        var memPol = tenantRuntimeSettingApplicationService.memoryPolicy(message.getTenantId());
        if (!memPol.abstractRefreshEnabled()) {
            return;
        }
        try {
            if (!dedupeAllow(message, memPol.resolvedAbstractDedupeTtlSeconds())) {
                return;
            }
            String json = objectMapper.writeValueAsString(message);
            if (rocketMqApp.isEnabled()) {
                var rocket = rocketMQTemplate.getIfAvailable();
                if (rocket != null) {
                    try {
                        rocket.syncSend(rocketMqApp.getMemoryAbstractTopic(), json);
                        return;
                    } catch (Exception e) {
                        log.warn("memory abstract rocket send failed, try redis/sync", e);
                    }
                }
            }
            var redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                redis.opsForList().rightPush(AiInternalResourceNames.RedisQueues.MEMORY_ABSTRACT, json);
                return;
            }
            if (memPol.abstractSyncFallback()) {
                userMemoryAbstractLlmWorker.runRefresh(message);
            }
        } catch (Exception e) {
            log.warn(
                    "memory abstract publish failed: {} — 排查：1) Sentinel NOAUTH 时配置 ai.redis.sentinel.password 或保证与 spring.data.redis.password 一致（EPP 会写入 sentinel.password）；"
                            + "2) cluster.nodes 须为数据端口(6379)，勿填 Sentinel(26379)；3) MOVED 时核对 cluster 模式；4) 网络/防火墙。",
                    summarizeRedisFailure(e),
                    e);
            if (memPol.abstractSyncFallback()) {
                try {
                    userMemoryAbstractLlmWorker.runRefresh(message);
                } catch (Exception ex) {
                    log.error("memory abstract sync fallback failed", ex);
                }
            }
        }
    }

    private boolean dedupeAllow(MemoryAbstractRefreshMessage message, long ttlSeconds) {
        var redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return true;
        }
        String key =
                "ai:memory:abstract:dedupe:"
                        + message.getTenantId()
                        + ":"
                        + message.getSubjectKey().trim();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
            return !Boolean.FALSE.equals(ok);
        } catch (Exception e) {
            log.warn(
                    "memory abstract dedupe redis skipped (继续尝试投递队列；若含 MOVED 请检查 ai.redis.mode=cluster 与 cluster.nodes): {}",
                    e.toString());
            return true;
        }
    }

    private static String summarizeRedisFailure(Exception e) {
        StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
        if (e.getMessage() != null) {
            sb.append(": ").append(e.getMessage());
        }
        Throwable c = e.getCause();
        if (c != null && c.getMessage() != null && !c.getMessage().isBlank()) {
            sb.append(" | cause=").append(c.getClass().getSimpleName()).append(": ").append(c.getMessage());
        }
        return sb.toString();
    }
}
