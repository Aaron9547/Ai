package com.aaron.cloud.chat.websearch.cache;

import com.aaron.cloud.chat.websearch.WebGroundingBundle;
import com.aaron.cloud.common.api.enums.chat.WebSearchCacheTier;

/** 联网缓存查找结果。 */
public record WebSearchGroundingCacheLookup(
        WebSearchCacheTier tier,
        WebGroundingBundle bundle,
        long fetchedAtEpochMs,
        boolean semanticNearMatch) {

    public boolean usable() {
        return tier != WebSearchCacheTier.MISS
                && tier != WebSearchCacheTier.EXPIRED
                && bundle != null;
    }
}
