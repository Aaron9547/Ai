package com.aaron.cloud.rag.crawl.fetch;

import com.aaron.cloud.rag.RagSitemapSeedSupport;
import com.aaron.cloud.rag.RagWebCrawlConstants;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/** 发现阶段 HTTP：统一走 {@link PolitenessGate} + {@link HttpFetcher}。 */
@Component
@RequiredArgsConstructor
public class CrawlDiscoveryPageFetcher {

    private static final int DISCOVERY_MAX_BODY_BYTES = 2_097_152;

    private final PolitenessGate politenessGate;
    private final HttpFetcher httpFetcher;

    public Document fetchHtmlDocument(String url, EffectiveSiteCrawlPolicy policy) throws Exception {
        String html = fetchHtml(url, policy);
        return Jsoup.parse(html, url);
    }

    public Document fetchXmlDocument(String url, EffectiveSiteCrawlPolicy policy) throws Exception {
        String body = fetchHtml(url, policy);
        return Jsoup.parse(body, url, Parser.xmlParser());
    }

    public List<String> fetchSitemapSeeds(
            String baseUrl, String rootDomain, int maxUrls, EffectiveSiteCrawlPolicy policy) {
        return RagSitemapSeedSupport.fetchSeedUrls(
                baseUrl,
                rootDomain,
                maxUrls,
                url -> fetchXmlDocument(url, policy));
    }

    private String fetchHtml(String url, EffectiveSiteCrawlPolicy policy) throws Exception {
        politenessGate.acquire(url, policy.politeness());
        try {
            EffectiveSiteCrawlPolicy.FetchPolicy fp = discoveryFetchPolicy(policy);
            var fetched =
                    httpFetcher.fetch(url, fp, siteReferer(url), null, null);
            return new String(fetched.body(), fetched.charset());
        } finally {
            politenessGate.release();
        }
    }

    static EffectiveSiteCrawlPolicy.FetchPolicy discoveryFetchPolicy(EffectiveSiteCrawlPolicy policy) {
        int maxBody = Math.min(DISCOVERY_MAX_BODY_BYTES, policy.fetch().maxBodyBytes());
        int timeout = Math.max(RagWebCrawlConstants.DISCOVERY_TIMEOUT_MS, policy.fetch().timeoutMs());
        return new EffectiveSiteCrawlPolicy.FetchPolicy(
                maxBody,
                timeout,
                Math.min(1, policy.fetch().metaRefreshMaxHops()),
                false,
                policy.fetch().sharedHttpClient());
    }

    private static String siteReferer(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            if (u.getHost() == null) {
                return null;
            }
            return u.getScheme() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return null;
        }
    }
}
