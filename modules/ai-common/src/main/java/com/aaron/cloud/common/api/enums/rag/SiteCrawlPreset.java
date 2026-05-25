package com.aaron.cloud.common.api.enums.rag;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** 租户站点爬取快捷档位（存 {@code SITE_CRAWL_PRESET}）。 */
public enum SiteCrawlPreset {
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE,
    CUSTOM;

    public static SiteCrawlPreset fromStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return BALANCED;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(p -> p.name().equals(t))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown site crawl preset: " + raw));
    }

    public static Optional<SiteCrawlPreset> tryFromStorage(String raw) {
        try {
            return Optional.of(fromStorage(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
