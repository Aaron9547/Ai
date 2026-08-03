package com.aaron.cloud.rag.crawl.policy;

import com.aaron.cloud.common.api.enums.rag.SiteCrawlPreset;
import com.aaron.cloud.common.api.ports.SiteCrawlRuntimePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocalSiteCrawlRuntimeAdapter implements SiteCrawlRuntimePort {

    private final SiteCrawlPolicyResolver siteCrawlPolicyResolver;

    @Override
    public String defaultRuntimeJsonForPreset(SiteCrawlPreset preset) {
        return siteCrawlPolicyResolver.defaultRuntimeJsonForPreset(preset);
    }
}
