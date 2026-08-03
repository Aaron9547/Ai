package com.aaron.cloud.common.platform;

import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey;
import com.aaron.cloud.common.api.enums.infra.PlatformSettingKey.SettingValueKind;
import com.aaron.cloud.common.platform.entity.SysPlatformSetting;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台级系统参数：枚举为真源（默认值 + 中文说明），持久化在 {@code sys_platform_setting}；进程内快照每 30 秒刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformSettingApplicationService {

    private final SysPlatformSettingRepository repository;

    private final AtomicReference<Map<PlatformSettingKey, String>> snapshot =
            new AtomicReference<>(Map.of());

    @PostConstruct
    void warmCache() {
        reloadSnapshot();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void scheduledReload() {
        reloadSnapshot();
    }

    public void reloadSnapshot() {
        try {
            Map<PlatformSettingKey, String> next = new EnumMap<>(PlatformSettingKey.class);
            for (PlatformSettingKey key : PlatformSettingKey.values()) {
                next.put(key, resolveFromDbOrDefault(key));
            }
            snapshot.set(Map.copyOf(next));
        } catch (Exception ex) {
            log.warn("platform settings snapshot reload failed, keep previous", ex);
        }
    }

    public String getEffectiveValueText(PlatformSettingKey key) {
        Map<PlatformSettingKey, String> map = snapshot.get();
        String cached = map.get(key);
        if (cached != null) {
            return cached;
        }
        return key.getDefaultValueText();
    }

    public boolean getBoolean(PlatformSettingKey key) {
        return "true".equalsIgnoreCase(getEffectiveValueText(key).trim());
    }

    public int getInt(PlatformSettingKey key) {
        try {
            return Integer.parseInt(getEffectiveValueText(key).trim());
        } catch (NumberFormatException ex) {
            return Integer.parseInt(key.getDefaultValueText());
        }
    }

    public long getLong(PlatformSettingKey key) {
        try {
            return Long.parseLong(getEffectiveValueText(key).trim());
        } catch (NumberFormatException ex) {
            return Long.parseLong(key.getDefaultValueText());
        }
    }

    public double getDouble(PlatformSettingKey key) {
        try {
            return Double.parseDouble(getEffectiveValueText(key).trim());
        } catch (NumberFormatException ex) {
            return Double.parseDouble(key.getDefaultValueText());
        }
    }

    public boolean isCronDue(PlatformSettingKey key) {
        return PlatformSettingCronSupport.isDueNow(getEffectiveValueText(key));
    }

    public List<PlatformSettingRow> listEffectiveRows() {
        List<PlatformSettingRow> out = new ArrayList<>();
        for (PlatformSettingKey key : PlatformSettingKey.values()) {
            out.add(
                    new PlatformSettingRow(
                            key.getStorage(),
                            key.getDescriptionZh(),
                            key.getValueKind().name(),
                            getEffectiveValueText(key),
                            key.getDefaultValueText()));
        }
        return out;
    }

    @Transactional
    public void replace(List<PutItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PutItem item : items) {
            if (item == null || item.getKey() == null || item.getKey().isBlank()) {
                continue;
            }
            PlatformSettingKey key =
                    PlatformSettingKey.fromStorage(item.getKey())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "unknown platform setting key: " + item.getKey()));
            String normalized = validateAndNormalize(key, item.getValueText());
            upsert(key, normalized, now);
        }
        reloadSnapshot();
    }

    private String resolveFromDbOrDefault(PlatformSettingKey key) {
        return repository
                .find(key)
                .map(SysPlatformSetting::getValueText)
                .filter(v -> v != null && !v.isBlank())
                .orElse(key.getDefaultValueText());
    }

    private void upsert(PlatformSettingKey key, String valueText, LocalDateTime now) {
        var existing = repository.find(key).orElse(null);
        if (existing == null) {
            var row = new SysPlatformSetting();
            row.setSettingKey(key);
            row.setValueText(valueText);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            repository.insert(row);
        } else {
            existing.setValueText(valueText);
            existing.setUpdatedAt(now);
            repository.updateById(existing);
        }
    }

    private static String validateAndNormalize(PlatformSettingKey key, String valueText) {
        String raw = valueText == null ? "" : valueText.trim();
        if (raw.isEmpty()) {
            return key.getDefaultValueText();
        }
        return switch (key.getValueKind()) {
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                    throw new IllegalArgumentException(key.getDescriptionZh() + " 须为 true 或 false");
                }
                yield raw.toLowerCase();
            }
            case INTEGER -> {
                try {
                    long n = Long.parseLong(raw);
                    if (n < 0) {
                        throw new NumberFormatException("negative");
                    }
                    yield Long.toString(n);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(key.getDescriptionZh() + " 须为非负整数");
                }
            }
            case STRING -> raw;
        };
    }

    @Data
    public static final class PutItem {
        private String key;
        private String valueText;
    }

    public record PlatformSettingRow(
            String key, String descriptionZh, String valueKind, String valueText, String defaultValueText) {}
}
