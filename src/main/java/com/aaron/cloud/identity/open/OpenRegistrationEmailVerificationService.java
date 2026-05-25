package com.aaron.cloud.identity.open;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 注册验证码：Redis 存储、发送冷却与一次性校验（TTL/冷却读租户配置）。 */
@Service
@RequiredArgsConstructor
public class OpenRegistrationEmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final TenantAuthRegisterVerificationResolver verificationResolver;

    public long getSendCooldownSeconds(long tenantId) {
        return verificationResolver.resolve(tenantId).getSendCooldownSeconds();
    }

    public long getCodeTtlMinutes(long tenantId) {
        long sec = verificationResolver.resolve(tenantId).getCodeTtlSeconds();
        return Math.max(1, (sec + 59) / 60);
    }

    public String issueCode(long tenantId, String normalizedEmail) {
        var cfg = verificationResolver.resolve(tenantId);
        int len = Math.max(4, Math.min(8, cfg.getCodeLength()));
        int bound = (int) Math.pow(10, len);
        String code = String.format("%0" + len + "d", RANDOM.nextInt(bound));
        long ttl = cfg.getCodeTtlSeconds();
        long cooldown = cfg.getSendCooldownSeconds();
        stringRedisTemplate
                .opsForValue()
                .set(codeKey(tenantId, normalizedEmail), code, Duration.ofSeconds(ttl));
        stringRedisTemplate
                .opsForValue()
                .set(cooldownKey(tenantId, normalizedEmail), "1", Duration.ofSeconds(cooldown));
        return code;
    }

    public long remainingCooldownSeconds(long tenantId, String normalizedEmail) {
        Long ttl = stringRedisTemplate.getExpire(cooldownKey(tenantId, normalizedEmail), TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return 0;
        }
        return ttl;
    }

    /** @return {@code true} 校验通过并已消费验证码 */
    public boolean verifyAndConsume(long tenantId, String normalizedEmail, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return false;
        }
        String key = codeKey(tenantId, normalizedEmail);
        String expected = stringRedisTemplate.opsForValue().get(key);
        if (expected == null || expected.isBlank()) {
            return false;
        }
        if (!expected.trim().equals(rawCode.trim())) {
            return false;
        }
        stringRedisTemplate.delete(key);
        return true;
    }

    public boolean hasPendingCode(long tenantId, String normalizedEmail) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(codeKey(tenantId, normalizedEmail)));
    }

    private static String codeKey(long tenantId, String email) {
        return "ai:auth:reg:code:" + tenantId + ":" + email;
    }

    private static String cooldownKey(long tenantId, String email) {
        return "ai:auth:reg:cooldown:" + tenantId + ":" + email;
    }
}
