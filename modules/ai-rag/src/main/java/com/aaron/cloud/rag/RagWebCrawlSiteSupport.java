package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.rag.RagWebCrawlSyncMode;
import com.aaron.cloud.common.rag.entity.RagWebCrawlSite;

public final class RagWebCrawlSiteSupport {

    private RagWebCrawlSiteSupport() {}

    public static RagWebCrawlSyncMode resolveEffectiveSyncMode(RagWebCrawlSite site) {
        if (site.getSyncMode() == RagWebCrawlSyncMode.FIRST_FULL_THEN_INCREMENTAL) {
            boolean done = site.getFirstRunDone() != null && site.getFirstRunDone() == 1;
            return done ? RagWebCrawlSyncMode.INCREMENTAL : RagWebCrawlSyncMode.FULL;
        }
        if (site.getSyncMode() == RagWebCrawlSyncMode.ALWAYS_FULL) {
            return RagWebCrawlSyncMode.FULL;
        }
        return site.getSyncMode() != null ? site.getSyncMode() : RagWebCrawlSyncMode.FULL;
    }
}
