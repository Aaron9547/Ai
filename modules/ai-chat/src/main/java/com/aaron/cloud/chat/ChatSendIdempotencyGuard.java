
package com.aaron.cloud.chat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 对话发送幂等：同一租户、会话、{@code clientSendKey} 在 TTL 内仅允许落库一条用户消息。
 * 无 Redis 时跳过（仅依赖前端防连点）。
 */
@Component
@RequiredArgsConstructor
public class ChatSendIdempotencyGuard {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

    /** @return {@code true} 表示可继续发送；{@code false} 表示重复键 */
    public boolean tryAcquire(long tenantId, long conversationId, String clientSendKey) {
        if (clientSendKey == null || clientSendKey.isBlank()) {
            return true;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return true;
        }
        String key = redisKey(tenantId, conversationId, clientSendKey.trim());
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", TTL);
        return Boolean.TRUE.equals(ok);
    }

    /** 用户消息尚未入库时释放键，便于用户修正后重试。 */
    public void release(long tenantId, long conversationId, String clientSendKey) {
        if (clientSendKey == null || clientSendKey.isBlank()) {
            return;
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        redis.delete(redisKey(tenantId, conversationId, clientSendKey.trim()));
    }

    private static String redisKey(long tenantId, long conversationId, String clientSendKey) {
        return "ai:chat:send:dedupe:" + tenantId + ":" + conversationId + ":" + clientSendKey;
    }
}
