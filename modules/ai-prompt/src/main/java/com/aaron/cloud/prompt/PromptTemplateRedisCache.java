package com.aaron.cloud.prompt;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class PromptTemplateRedisCache {

    private final StringRedisTemplate redis;

    @Value("${ai.prompt-template.cache-ttl-seconds:600}")
    private long cacheTtlSeconds;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public record CacheStats(long approximateKeyCount, long hits, long misses) {}

    private static String cacheKey(long tenantId, String promptCode, String locale) {
        return "ai:prompt:" + tenantId + ":" + promptCode + ":" + locale;
    }

    public String getOrNull(long tenantId, String promptCode, String locale) {
        try {
            return redis.opsForValue().get(cacheKey(tenantId, promptCode, locale));
        } catch (Exception e) {
            log.warn("prompt template redis get failed tenant={} code={}", tenantId, promptCode, e);
            return null;
        }
    }

    public void put(long tenantId, String promptCode, String locale, String content) {
        try {
            long ttl = Math.max(30L, cacheTtlSeconds);
            redis.opsForValue().set(cacheKey(tenantId, promptCode, locale), content, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("prompt template redis put failed tenant={} code={}", tenantId, promptCode, e);
        }
    }

    public void evict(long tenantId, String promptCode, String locale) {
        try {
            if (promptCode == null || promptCode.isBlank()) {
                return;
            }
            redis.delete(cacheKey(tenantId, promptCode, locale));
            if (!"*".equals(locale)) {
                redis.delete(cacheKey(tenantId, promptCode, "*"));
            }
        } catch (Exception e) {
            log.warn("prompt template redis evict failed tenant={} code={}", tenantId, promptCode, e);
        }
    }

    public void evictAll() {
        try {
            var keys = redis.keys("ai:prompt:*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("prompt template redis evictAll failed", e);
        }
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public CacheStats stats() {
        long keyCount = 0;
        try {
            var keys = redis.keys("ai:prompt:*");
            keyCount = keys == null ? 0 : keys.size();
        } catch (Exception e) {
            log.warn("prompt template redis stats failed", e);
        }
        return new CacheStats(keyCount, hits.get(), misses.get());
    }
}
