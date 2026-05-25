package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.common.api.enums.rag.CrawlQueueRole;
import java.util.ArrayList;
import java.util.List;

public record DiscoveredUrl(String url, String title, CrawlQueueRole role, List<String> sources) {

    public DiscoveredUrl(String url, String title, CrawlQueueRole role, String source) {
        this(url, title, role, List.of(source));
    }

    public DiscoveredUrl withMergedSource(String source) {
        List<String> merged = new ArrayList<>(sources);
        if (!merged.contains(source)) {
            merged.add(source);
        }
        return new DiscoveredUrl(url, title, role, merged);
    }
}
