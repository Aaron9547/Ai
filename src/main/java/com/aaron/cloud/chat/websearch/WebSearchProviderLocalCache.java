package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.api.enums.llm.WebSearchFixedSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 内置固定联网源进程内短缓存（默认 10 分钟），减轻同关键词高频外呼；与 Redis 联网 grounding 缓存互补。
 */
@Component
public class WebSearchProviderLocalCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int MAX_ENTRIES = 2_000;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public Optional<WebGroundingBundle> get(long tenantId, WebSearchFixedSource source, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank() || source == null) {
            return Optional.empty();
        }
        String key = cacheKey(tenantId, source, normalizedQuery);
        Entry e = store.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(e.expiresAt())) {
            store.remove(key, e);
            return Optional.empty();
        }
        return Optional.of(e.bundle());
    }

    public void put(long tenantId, WebSearchFixedSource source, String normalizedQuery, WebGroundingBundle bundle) {
        if (normalizedQuery == null
                || normalizedQuery.isBlank()
                || source == null
                || bundle == null
                || isEmpty(bundle)) {
            return;
        }
        if (store.size() >= MAX_ENTRIES) {
            store.clear();
        }
        String key = cacheKey(tenantId, source, normalizedQuery);
        store.put(key, new Entry(bundle, Instant.now().plus(DEFAULT_TTL)));
    }

    private static boolean isEmpty(WebGroundingBundle bundle) {
        boolean noRefs = bundle.references() == null || bundle.references().isEmpty();
        boolean noSummary = bundle.summaryText() == null || bundle.summaryText().isBlank();
        return noRefs && noSummary;
    }

    private static String cacheKey(long tenantId, WebSearchFixedSource source, String normalizedQuery) {
        return tenantId + "|" + source.getCode() + "|" + normalizedQuery.trim();
    }

    private record Entry(WebGroundingBundle bundle, Instant expiresAt) {}
}
