package com.aaron.cloud.common.api.enums.chat;

/** 联网缓存条目新鲜度档位（滚动小时，非自然日）。 */
public enum WebSearchCacheTier {
    MISS,
    FRESH,
    WARM,
    STALE,
    EXPIRED
}
