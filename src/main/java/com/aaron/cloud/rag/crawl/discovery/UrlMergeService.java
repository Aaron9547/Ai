package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.rag.crawl.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UrlMergeService {

    public record MergedUrl(
            String url, String title, CrawlQueueRole role, List<String> sources, int score) {}

    public List<MergedUrl> merge(List<DiscoveredUrl> raw, EffectiveSiteCrawlPolicy.DiscoveryPolicy policy) {
        Map<String, MergedUrl> byNorm = new LinkedHashMap<>();
        for (DiscoveredUrl u : raw) {
            if (u.url() == null || u.url().isBlank()) {
                continue;
            }
            String norm = RagWebCrawlUrlSupport.normalizeUrl(u.url());
            if (norm.isEmpty()) {
                continue;
            }
            MergedUrl existing = byNorm.get(norm);
            if (existing == null) {
                byNorm.put(
                        norm,
                        new MergedUrl(
                                u.url(),
                                u.title(),
                                u.role(),
                                new ArrayList<>(u.sources()),
                                scoreFor(u, policy)));
            } else {
                List<String> sources = new ArrayList<>(existing.sources());
                for (String s : u.sources()) {
                    if (!sources.contains(s)) {
                        sources.add(s);
                    }
                }
                String title =
                        (u.title() != null && !u.title().isBlank())
                                ? u.title()
                                : existing.title();
                CrawlQueueRole role =
                        existing.role() == CrawlQueueRole.ARTICLE || u.role() == CrawlQueueRole.ARTICLE
                                ? CrawlQueueRole.ARTICLE
                                : CrawlQueueRole.EXPLORE;
                DiscoveredUrl mergedProbe = new DiscoveredUrl(existing.url(), title, role, sources);
                byNorm.put(
                        norm,
                        new MergedUrl(existing.url(), title, role, sources, scoreFor(mergedProbe, policy)));
            }
        }
        return byNorm.values().stream()
                .sorted(Comparator.comparingInt(MergedUrl::score).reversed())
                .toList();
    }

    private static int scoreFor(DiscoveredUrl u, EffectiveSiteCrawlPolicy.DiscoveryPolicy policy) {
        int score = u.sources().size();
        if (policy.urlMerge().boostMultiSource()
                && score >= policy.urlMerge().minSourcesForBoost()) {
            score += 2;
        }
        if (u.role() == CrawlQueueRole.ARTICLE) {
            score += 1;
        }
        return score;
    }
}
