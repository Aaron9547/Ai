package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.api.enums.tenant.TenantRuntimeSettingKey;
import com.aaron.cloud.common.infra.AiInternalResourceNames;
import com.aaron.cloud.common.platform.PlatformSettingApplicationService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 租户运行时配置的读穿缓存；写入路径由 {@link TenantRuntimeSettingApplicationService} 对同一逻辑键做「双删」
 * （写库前删、写库后再删），降低并发下旧值回填缓存的概率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class TenantRuntimeSettingRedisCache {

    private final StringRedisTemplate redis;
    private final PlatformSettingApplicationService platformSettings;

    private static String cacheKey(long tenantId, TenantRuntimeSettingKey key) {
        return AiInternalResourceNames.RedisKeyPrefixes.TENANT_RUNTIME_CFG + tenantId + ":" + key.getStorage();
    }

    public String getOrNull(long tenantId, TenantRuntimeSettingKey key) {
        try {
            return redis.opsForValue().get(cacheKey(tenantId, key));
        } catch (Exception e) {
            log.warn("tenant runtime setting redis get failed tenant={} key={}", tenantId, key.getStorage(), e);
            return null;
        }
    }

    public void put(long tenantId, TenantRuntimeSettingKey key, String valueText) {
        try {
            long ttl =
                    Math.max(
                            30L,
                            platformSettings.getLong(PlatformSettingKey.TENANT_RUNTIME_CACHE_TTL_SECONDS));
            redis.opsForValue().set(cacheKey(tenantId, key), valueText, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("tenant runtime setting redis put failed tenant={} key={}", tenantId, key.getStorage(), e);
        }
    }

    public void evict(long tenantId, TenantRuntimeSettingKey key) {
        try {
            redis.delete(cacheKey(tenantId, key));
        } catch (Exception e) {
            log.warn("tenant runtime setting redis evict failed tenant={} key={}", tenantId, key.getStorage(), e);
        }
    }
}
