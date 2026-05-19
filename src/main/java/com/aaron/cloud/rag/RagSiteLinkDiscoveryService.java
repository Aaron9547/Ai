package com.aaron.cloud.rag;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * 本地规则发现站点文章链接：同根域 BFS + 正文页启发式（不依赖外部 LLM）。
 *
 * <p>语义对齐 ly-ai-application {@code crawlArticleLinksLocal}，实现为可维护的简化版。
 */
@Slf4j
@Service
public class RagSiteLinkDiscoveryService {

    private static final int MAX_EXPLORE_PAGES = 2_000;
    private static final int FETCH_TIMEOUT_MS = 15_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AiRagLocalCrawler/1.0; +https://ly-ai.local)";

    public List<String> discoverArticleLinks(String baseUrl, Integer maxDepth) {
        String normBase = RagWebCrawlUrlSupport.normalizeUrl(baseUrl);
        if (normBase.isEmpty()) {
            throw new IllegalArgumentException("baseUrl 须为完整 http/https 地址");
        }
        URI baseUri;
        try {
            baseUri = new URI(normBase);
        } catch (Exception e) {
            throw new IllegalArgumentException("baseUrl 格式不正确");
        }
        if (baseUri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl 格式不正确");
        }
        String rootDomain = RagWebCrawlUrlSupport.resolveRootDomain(normBase);
        int depthLimit = RagWebCrawlUrlSupport.normalizeDepth(maxDepth);

        Set<String> visited = new HashSet<>();
        LinkedHashSet<String> articleUrls = new LinkedHashSet<>();
        Deque<DepthUrl> queue = new ArrayDeque<>();
        queue.add(new DepthUrl(normBase, 1));

        while (!queue.isEmpty() && visited.size() < MAX_EXPLORE_PAGES) {
            DepthUrl node = queue.poll();
            if (node == null || node.depth > depthLimit) {
                continue;
            }
            String current = node.url;
            if (!visited.add(current)) {
                continue;
            }
            try {
                Document doc =
                        Jsoup.connect(current)
                                .userAgent(USER_AGENT)
                                .timeout(FETCH_TIMEOUT_MS)
                                .followRedirects(true)
                                .get();
                if (looksLikeArticlePage(doc, current)) {
                    articleUrls.add(current);
                }
                if (node.depth < depthLimit) {
                    for (String child : extractSameDomainLinks(doc, current, rootDomain)) {
                        if (!visited.contains(child)) {
                            queue.add(new DepthUrl(child, node.depth + 1));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("本地规则探索页面失败 url={} err={}", current, e.toString());
            }
        }
        log.info(
                "本地规则链接发现完成 baseUrl={} depth={} exploredPages={} articleUrls={}",
                normBase,
                depthLimit,
                visited.size(),
                articleUrls.size());
        return new ArrayList<>(articleUrls);
    }

    private static boolean looksLikeArticlePage(Document doc, String url) {
        if (doc.selectFirst("article") != null) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.matches(".*/(news|article|content|info|detail|show|post|blog)/.*")) {
            return true;
        }
        if (lower.matches(".*\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}.*")) {
            return true;
        }
        String text = doc.body() != null ? doc.body().text() : "";
        return text.length() >= 280;
    }

    private static List<String> extractSameDomainLinks(Document doc, String pageUrl, String rootDomain) {
        List<String> out = new ArrayList<>();
        Elements links = doc.select("a[href]");
        for (Element a : links) {
            String abs = a.absUrl("href");
            if (abs.isBlank()) {
                continue;
            }
            String norm = RagWebCrawlUrlSupport.normalizeUrl(abs);
            if (norm.isEmpty()
                    || !norm.startsWith("http")
                    || RagWebCrawlUrlSupport.isStaticResource(norm)
                    || !RagWebCrawlUrlSupport.isInRootDomain(norm, rootDomain)) {
                continue;
            }
            out.add(norm);
        }
        return out;
    }

    private record DepthUrl(String url, int depth) {}
}
