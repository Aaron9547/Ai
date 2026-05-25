package com.aaron.cloud.common.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 基于 Redis {@code SET NX EX} 的简易分布式锁；释放时用 Lua 校验 token，避免误删其它实例的锁。
 *
 * <p>无 Redis 时降级为 no-op（单实例开发环境仍可运行；生产多实例须配置 Redis）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
                    """,
                    Long.class);

    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

    /**
     * 尝试加锁；成功则返回可 {@link AutoCloseable#close()} 的句柄以释放锁。
     *
     * @return empty 表示未获得锁；no-op 句柄表示无 Redis、未真正加锁
     */
    public Optional<DistributedLockHandle> tryAcquire(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("lock key required");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("lock ttl must be positive");
        }
        StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
        if (redis == null) {
            log.debug("redis unavailable, distributed lock skipped key={}", key);
            return Optional.of(DistributedLockHandle.noop(key));
        }
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
            if (Boolean.TRUE.equals(ok)) {
                return Optional.of(new DistributedLockHandle(this, key, token, redis, false));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("tryAcquire lock failed key={} err={}", key, e.toString());
            return Optional.of(DistributedLockHandle.noop(key));
        }
    }

    void release(DistributedLockHandle handle) {
        if (handle == null || handle.noop()) {
            return;
        }
        try {
            redisExecuteUnlock(handle.redis(), handle.key(), handle.token());
        } catch (Exception e) {
            log.warn("release lock failed key={} err={}", handle.key(), e.toString());
        }
    }

    private static void redisExecuteUnlock(StringRedisTemplate redis, String key, String token) {
        redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }

    /** 锁句柄：{@code try (var h = ...) { ... }} 结束时自动释放。 */
    public static final class DistributedLockHandle implements AutoCloseable {

        private final RedisDistributedLockService service;
        private final String key;
        private final String token;
        private final StringRedisTemplate redis;
        private final boolean noop;

        DistributedLockHandle(
                RedisDistributedLockService service,
                String key,
                String token,
                StringRedisTemplate redis,
                boolean noop) {
            this.service = service;
            this.key = key;
            this.token = token;
            this.redis = redis;
            this.noop = noop;
        }

        static DistributedLockHandle noop(String key) {
            return new DistributedLockHandle(null, key, null, null, true);
        }

        boolean noop() {
            return noop;
        }

        String key() {
            return key;
        }

        String token() {
            return token;
        }

        StringRedisTemplate redis() {
            return redis;
        }

        @Override
        public void close() {
            if (service != null) {
                service.release(this);
            }
        }
    }
}
