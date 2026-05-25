package com.aaron.cloud.rag.crawl.discovery;

import com.aaron.cloud.rag.RagWebCrawlUrlSupport;
import com.aaron.cloud.common.api.enums.rag.CrawlQueueRole;
import com.aaron.cloud.rag.crawl.fetch.CrawlDiscoveryPageFetcher;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RssAtomDiscoveryStrategy implements CrawlDiscoveryStrategy {

    private final CrawlDiscoveryPageFetcher discoveryPageFetcher;

    @Override
    public String id() {
        return "rss_atom";
    }

    @Override
    public List<DiscoveredUrl> discover(DiscoveryContext ctx) {
        String base = ctx.baseUrl();
        String root = RagWebCrawlUrlSupport.resolveRootDomain(RagWebCrawlUrlSupport.normalizeUrl(base));
        if (root == null) {
            return List.of();
        }
        List<String> feedUrls = guessFeedUrls(base);
        List<DiscoveredUrl> out = new ArrayList<>();
        for (String feed : feedUrls) {
            try {
                Document doc = discoveryPageFetcher.fetchXmlDocument(feed, ctx.policy());
                collectEntries(doc, root, out);
            } catch (Exception ignored) {
                // skip feed
            }
        }
        return out;
    }

    private static List<String> guessFeedUrls(String baseUrl) {
        String origin;
        try {
            URI u = URI.create(baseUrl.trim());
            origin = u.getScheme() + "://" + u.getHost();
        } catch (Exception e) {
            return List.of();
        }
        return List.of(origin + "/rss", origin + "/feed", origin + "/atom.xml", origin + "/rss.xml");
    }

    private static void collectEntries(Document doc, String rootDomain, List<DiscoveredUrl> out) {
        Elements links = doc.select("item > link, entry > link[href], item > guid, entry > id");
        for (Element el : links) {
            String href = el.hasAttr("href") ? el.attr("href") : el.text();
            if (href == null || href.isBlank()) {
                continue;
            }
            String abs = href;
            if (!href.startsWith("http")) {
                try {
                    abs = URI.create(baseFrom(doc)).resolve(href).toString();
                } catch (Exception e) {
                    continue;
                }
            }
            if (!RagWebCrawlUrlSupport.isInRootDomain(abs, rootDomain)) {
                continue;
            }
            String norm = RagWebCrawlUrlSupport.normalizeUrl(abs);
            if (!norm.isEmpty()) {
                out.add(new DiscoveredUrl(norm, null, CrawlQueueRole.ARTICLE, "rss_atom"));
            }
        }
    }

    private static String baseFrom(Document doc) {
        String base = doc.baseUri();
        return base != null ? base : "";
    }
}
