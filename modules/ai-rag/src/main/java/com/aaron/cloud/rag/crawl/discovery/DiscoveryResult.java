package com.aaron.cloud.rag.crawl.discovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 多策略发现合并结果。 */
public record DiscoveryResult(
        List<UrlMergeService.MergedUrl> merged, Map<String, Integer> discoveryByStrategy) {

    public static DiscoveryResult empty() {
        return new DiscoveryResult(List.of(), new LinkedHashMap<>());
    }
}
