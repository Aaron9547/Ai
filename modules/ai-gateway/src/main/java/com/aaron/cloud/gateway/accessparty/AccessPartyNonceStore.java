package com.aaron.cloud.gateway.accessparty;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccessPartyNonceStore {

    private static final Duration NONCE_TTL = Duration.ofSeconds(600);

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public AccessPartyNonceStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    /** @return true 若 nonce 首次使用 */
    public boolean tryConsume(String appId, String nonce) {
        if (appId == null || appId.isBlank() || nonce == null || nonce.isBlank()) {
            return false;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return true;
        }
        String key = "ai:gw:ap:nonce:" + appId.trim() + ":" + nonce.trim();
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", NONCE_TTL);
        return Boolean.TRUE.equals(ok);
    }
}
