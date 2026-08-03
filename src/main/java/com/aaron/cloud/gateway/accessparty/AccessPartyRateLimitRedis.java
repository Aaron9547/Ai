package com.aaron.cloud.gateway.accessparty;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccessPartyRateLimitRedis {

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public AccessPartyRateLimitRedis(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    public boolean tryAcquire(String key, int cap) {
        if (cap <= 0) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return true;
        }
        Long n = redis.opsForValue().increment(key);
        if (n == null) {
            return true;
        }
        if (n == 1L) {
            redis.expire(key, java.time.Duration.ofMinutes(2));
        }
        return n <= cap;
    }
}
