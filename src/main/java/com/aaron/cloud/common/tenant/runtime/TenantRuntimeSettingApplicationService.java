package com.aaron.cloud.common.tenant.runtime;

import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey;
import com.aaron.cloud.common.api.enums.TenantRuntimeSettingKey.SettingValueKind;
import com.aaron.cloud.common.tenant.runtime.entity.TenRuntimeSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TenantRuntimeSettingApplicationService {

    private final TenRuntimeSettingRepository repository;
    private final ObjectProvider<TenantRuntimeSettingRedisCache> redisCache;

    public boolean isAuthOpenRegistrationEnabled(long tenantId) {
        String raw = effectiveValueText(tenantId, TenantRuntimeSettingKey.AUTH_OPEN_REGISTRATION);
        return Boolean.parseBoolean(raw.trim());
    }

    public List<TenantRuntimeSettingRow> listEffectiveRows(long tenantId) {
        List<TenantRuntimeSettingRow> out = new ArrayList<>();
        for (TenantRuntimeSettingKey key : TenantRuntimeSettingKey.values()) {
            String vt = effectiveValueText(tenantId, key);
            out.add(
                    new TenantRuntimeSettingRow(
                            key.getStorage(),
                            vt,
                            key.getDescriptionZh(),
                            key.getValueKind().name()));
        }
        return out;
    }

    /**
     * 批量写入指定租户的运行参数（仅操作传入的 {@code tenantId} 对应行）。启用 Redis 时对该租户每个变更键执行
     * <strong>缓存双删</strong>：写库前 {@code evict} 一次、持久化后再 {@code evict} 一次，避免并发读穿把旧值写回缓存。
     */
    public void replace(long tenantId, List<PutItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items required");
        }
        for (PutItem item : items) {
            TenantRuntimeSettingKey key =
                    TenantRuntimeSettingKey.fromStorage(item.getKey())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST, "unknown setting key: " + item.getKey()));
            String normalized = validateAndNormalize(key, item.getValueText());
            evictRedis(tenantId, key);
            upsertRow(tenantId, key, normalized);
            evictRedis(tenantId, key);
        }
    }

    private void evictRedis(long tenantId, TenantRuntimeSettingKey key) {
        redisCache.ifAvailable(c -> c.evict(tenantId, key));
    }

    private void upsertRow(long tenantId, TenantRuntimeSettingKey key, String valueText) {
        Optional<TenRuntimeSetting> existing = repository.find(tenantId, key);
        if (existing.isPresent()) {
            TenRuntimeSetting row = existing.get();
            row.setValueText(valueText);
            repository.updateById(row);
        } else {
            TenRuntimeSetting row = new TenRuntimeSetting();
            row.setTenantId(tenantId);
            row.setSettingKey(key);
            row.setValueText(valueText);
            repository.insert(row);
        }
    }

    private String effectiveValueText(long tenantId, TenantRuntimeSettingKey key) {
        TenantRuntimeSettingRedisCache cache = redisCache.getIfAvailable();
        if (cache != null) {
            String cached = cache.getOrNull(tenantId, key);
            if (cached != null) {
                return cached;
            }
        }
        String fromDb =
                repository.find(tenantId, key).map(TenRuntimeSetting::getValueText).orElse(key.defaultValueText());
        if (cache != null) {
            cache.put(tenantId, key, fromDb);
        }
        return fromDb;
    }

    private static String validateAndNormalize(TenantRuntimeSettingKey key, String valueText) {
        if (valueText == null || valueText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valueText required for " + key.getStorage());
        }
        if (key.getValueKind() == SettingValueKind.BOOLEAN) {
            String t = valueText.trim().toLowerCase(Locale.ROOT);
            if (!"true".equals(t) && !"false".equals(t)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "boolean setting must be true or false: " + key.getStorage());
            }
            return t;
        }
        return valueText.trim();
    }

    public record TenantRuntimeSettingRow(
            String key, String valueText, String descriptionZh, String valueKind) {}

    @Data
    public static final class PutItem {
        private String key;
        private String valueText;
    }
}
