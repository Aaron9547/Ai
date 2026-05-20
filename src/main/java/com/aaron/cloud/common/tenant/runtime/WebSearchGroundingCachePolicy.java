package com.aaron.cloud.common.tenant.runtime;

/**
 * 联网检索 Redis 缓存策略（租户 {@code WEB_SEARCH_GROUNDING_CACHE_JSON}；未配置时用 {@link #defaults()}）。
 *
 * @param freshHours 0～该小时数：命中则 0 次外呼
 * @param warmHours 超过 fresh、不超过 warm：最多 1 轮外呼并与缓存合并
 * @param staleHours 超过 warm、不超过 stale：最多 1 轮；超过 stale：视为过期，全量轮数
 * @param conversationReuseHours 会话内相同问句复用窗口（方案 D）
 */
public record WebSearchGroundingCachePolicy(
        boolean enabled,
        int freshHours,
        int warmHours,
        int staleHours,
        boolean semanticEnabled,
        double similarityThreshold,
        int indexMaxEntries,
        int conversationReuseHours) {

    public static WebSearchGroundingCachePolicy defaults() {
        return new WebSearchGroundingCachePolicy(true, 6, 24, 48, true, 0.88, 300, 6);
    }

    public WebSearchCacheTier tierForAgeMs(long ageMs) {
        if (ageMs < 0L) {
            return WebSearchCacheTier.MISS;
        }
        long freshMs = hoursToMs(freshHours);
        long warmMs = hoursToMs(warmHours);
        long staleMs = hoursToMs(staleHours);
        if (ageMs <= freshMs) {
            return WebSearchCacheTier.FRESH;
        }
        if (ageMs <= warmMs) {
            return WebSearchCacheTier.WARM;
        }
        if (ageMs <= staleMs) {
            return WebSearchCacheTier.STALE;
        }
        return WebSearchCacheTier.EXPIRED;
    }

    /** 在已有缓存条目前提下，本轮应执行的联网 API 轮数（不超过租户配置的 {@code rounds}）。 */
    public int effectiveRoundsForTier(WebSearchCacheTier tier, int configuredRounds) {
        int cap = Math.clamp(configuredRounds, 1, 10);
        return switch (tier) {
            case FRESH -> 0;
            case WARM, STALE -> Math.min(1, cap);
            case MISS, EXPIRED -> cap;
        };
    }

    private static long hoursToMs(int hours) {
        return Math.max(0, hours) * 3_600_000L;
    }
}
