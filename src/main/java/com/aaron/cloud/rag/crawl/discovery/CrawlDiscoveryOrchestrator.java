package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlDiscoveryOrchestrator {

    private final List<CrawlDiscoveryStrategy> strategies;
    private final UrlMergeService urlMergeService;

    public DiscoveryResult discover(String baseUrl, Integer maxDepth, EffectiveSiteCrawlPolicy policy) {
        List<DiscoveredUrl> raw = new ArrayList<>();
        Map<String, Integer> byStrategy = new LinkedHashMap<>();
        var ctx = new CrawlDiscoveryStrategy.DiscoveryContext(baseUrl, maxDepth, policy);
        boolean hasHtmlBfs =
                policy.discovery().strategies().stream().anyMatch(s -> "html_bfs".equalsIgnoreCase(s));
        for (String configuredId : policy.discovery().strategies()) {
            if (hasHtmlBfs && "list_pagination".equalsIgnoreCase(configuredId)) {
                byStrategy.putIfAbsent(configuredId, 0);
                continue;
            }
            boolean ran = false;
            for (CrawlDiscoveryStrategy strategy : strategies) {
                if (!strategy.supports(configuredId)) {
                    continue;
                }
                ran = true;
                try {
                    List<DiscoveredUrl> found = strategy.discover(ctx);
                    int n = found == null ? 0 : found.size();
                    byStrategy.merge(configuredId, n, Integer::sum);
                    if (found != null) {
                        raw.addAll(found);
                    }
                } catch (Exception e) {
                    log.warn("发现策略失败 id={} err={}", configuredId, e.toString());
                    byStrategy.merge(configuredId, 0, Integer::sum);
                }
                break;
            }
            if (!ran) {
                log.warn("未注册的发现策略: {}", configuredId);
            }
        }
        List<UrlMergeService.MergedUrl> merged = urlMergeService.merge(raw, policy.discovery());
        return new DiscoveryResult(merged, byStrategy);
    }
}
