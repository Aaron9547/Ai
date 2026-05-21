package com.aaron.cloud.rag;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.aaron.cloud.rag.crawl.fetch.CrawlDiscoveryPageFetcher;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.aaron.cloud.rag.crawl.policy.SiteCrawlPreset;
import com.aaron.cloud.rag.crawl.policy.SiteCrawlPresetTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * 站点爬取链接发现（对齐 ly-ai-application {@code BatchCrawlHandler#loopGetArticleLinksByDepthLocal}）。
 *
 * <ul>
 *   <li>按「层」探索：第 N 层只处理当层入口页（首页/栏目），不把首页当作入库文章。</li>
 *   <li>列表页：在同一层内跟完全部分页（下一页 / 页码 / 尾页），收集全部文章 URL。</li>
 *   <li>文章 URL 才进入最终列表；栏目/列表链仅在 depth 未用尽时进入下一层。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSiteLinkDiscoveryService {

    private static final EffectiveSiteCrawlPolicy LEGACY_POLICY =
            SiteCrawlPresetTemplates.template(SiteCrawlPreset.BALANCED);

    private final CrawlDiscoveryPageFetcher discoveryPageFetcher;

    private static final int MAX_EXPLORE_PAGES = 2_000;
    private static final int MAX_PAGINATION_PAGES_PER_LIST = 80;
    private static final int SITEMAP_SEED_MAX = 400;

    /**
     * 发现待入库文章 URL（非整站每页都入库）。
     *
     * @param maxDepth 探索层数：1=仅处理入口页及其列表全部分页上的文章；2=再多探索一层栏目链接
     */
    public List<String> discoverSiteUrls(String baseUrl, Integer maxDepth) {
        return discoverSiteUrls(baseUrl, maxDepth, LEGACY_POLICY);
    }

    public List<String> discoverSiteUrls(
            String baseUrl, Integer maxDepth, EffectiveSiteCrawlPolicy policy) {
        return new ArrayList<>(collectArticleMap(baseUrl, maxDepth, policy).keySet());
    }

    /** 发现文章 URL 及列表页解析出的标题（VSB 图文列表等）。 */
    public List<RagCrawlPageLink> discoverSiteArticles(String baseUrl, Integer maxDepth) {
        return discoverSiteArticles(baseUrl, maxDepth, LEGACY_POLICY);
    }

    public List<RagCrawlPageLink> discoverSiteArticles(
            String baseUrl, Integer maxDepth, EffectiveSiteCrawlPolicy policy) {
        LinkedHashMap<String, String> map = collectArticleMap(baseUrl, maxDepth, policy);
        List<RagCrawlPageLink> out = new ArrayList<>(map.size());
        for (var e : map.entrySet()) {
            out.add(new RagCrawlPageLink(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** 仅拉取入口页并统计静态文章链接数（供 JS 渲染阈值判断，避免完整 BFS）。 */
    public int countStaticArticleLinksOnEntry(String baseUrl, EffectiveSiteCrawlPolicy policy) {
        try {
            Document doc = fetchDocument(baseUrl, policy);
            String root = RagWebCrawlUrlSupport.resolveRootDomain(baseUrl);
            int n = 0;
            for (RagCrawlPageLink link : extractPageLinks(doc, baseUrl)) {
                if (!RagArticleLinkHeuristics.looksLikeArticle(link)) {
                    continue;
                }
                String norm = RagWebCrawlUrlSupport.normalizeUrl(link.href());
                if (!norm.isEmpty() && RagWebCrawlUrlSupport.isInRootDomain(norm, root)) {
                    n++;
                }
            }
            return n;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private LinkedHashMap<String, String> collectArticleMap(
            String baseUrl, Integer maxDepth, EffectiveSiteCrawlPolicy policy) {
        String normBase = RagWebCrawlUrlSupport.normalizeUrl(baseUrl);
        if (normBase.isEmpty()) {
            throw new IllegalArgumentException("baseUrl 须为完整 http/https 地址");
        }
        try {
            URI baseUri = new URI(normBase);
            if (baseUri.getHost() == null) {
                throw new IllegalArgumentException("baseUrl 格式不正确");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("baseUrl 格式不正确");
        }
        String rootDomain = RagWebCrawlUrlSupport.resolveRootDomain(normBase);
        int depthLimit = RagWebCrawlUrlSupport.normalizeDepth(maxDepth);

        Set<String> visitedExplore = new HashSet<>();
        Set<String> queuedExplore = new HashSet<>();
        LinkedHashMap<String, String> articleUrls = new LinkedHashMap<>();
        List<String> currentLevel = new ArrayList<>();
        currentLevel.add(normBase);
        queuedExplore.add(normBase);
        mergeSitemapSeeds(normBase, rootDomain, currentLevel, queuedExplore, policy);

        for (int depth = 1; depth <= depthLimit && !currentLevel.isEmpty(); depth++) {
            LinkedHashSet<String> nextLevel = new LinkedHashSet<>();
            for (String pageUrl : currentLevel) {
                if (pageUrl == null || pageUrl.isBlank() || !visitedExplore.add(pageUrl)) {
                    continue;
                }
                int exploreCap = Math.min(MAX_EXPLORE_PAGES, policy.discovery().maxExplorePages());
                if (visitedExplore.size() > exploreCap) {
                    log.warn("站点探索达到页面上限 {}，提前结束 baseUrl={}", exploreCap, normBase);
                    break;
                }
                try {
                    PageHarvest harvest = harvestListAndArticles(pageUrl, rootDomain, normBase, policy);
                    for (RagCrawlPageLink article : harvest.articleLinks()) {
                        String norm = RagWebCrawlUrlSupport.normalizeUrl(article.href());
                        if (norm.isEmpty() || isExcludedCrawlUrl(norm)) {
                            continue;
                        }
                        String t = article.title() == null ? "" : article.title().trim();
                        articleUrls.putIfAbsent(norm, t);
                    }
                    if (depth < depthLimit) {
                        for (String child : harvest.nextLevelUrls()) {
                            if (!visitedExplore.contains(child)
                                    && !queuedExplore.contains(child)
                                    && !isExcludedCrawlUrl(child)) {
                                queuedExplore.add(child);
                                nextLevel.add(child);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("站点层探索失败 url={} depth={} err={}", pageUrl, depth, e.toString());
                }
            }
            currentLevel = new ArrayList<>(nextLevel);
        }
        log.info(
                "站点文章链接发现完成 baseUrl={} depthLimit={} exploredPages={} articleUrls={}",
                normBase,
                depthLimit,
                visitedExplore.size(),
                articleUrls.size());
        return articleUrls;
    }

    /** @deprecated 请使用 {@link #discoverSiteUrls}。 */
    @Deprecated
    public List<String> discoverArticleLinks(String baseUrl, Integer maxDepth) {
        return discoverSiteUrls(baseUrl, maxDepth);
    }

    private void mergeSitemapSeeds(
            String normBase,
            String rootDomain,
            List<String> currentLevel,
            Set<String> queuedExplore,
            EffectiveSiteCrawlPolicy policy) {
        try {
            int maxSeeds = Math.min(SITEMAP_SEED_MAX, policy.discovery().maxSitemapSeeds());
            List<String> seeds =
                    discoveryPageFetcher.fetchSitemapSeeds(normBase, rootDomain, maxSeeds, policy);
            for (String seed : seeds) {
                String n = RagWebCrawlUrlSupport.normalizeUrl(seed);
                if (n.isEmpty() || isExcludedCrawlUrl(n)) {
                    continue;
                }
                if (queuedExplore.add(n)) {
                    currentLevel.add(n);
                }
            }
            if (!seeds.isEmpty()) {
                log.info("已合并 sitemap 种子 baseUrl={} seedCount={}", normBase, seeds.size());
            }
        } catch (Exception e) {
            log.debug("合并 sitemap 种子失败(忽略) baseUrl={} err={}", normBase, e.toString());
        }
    }

    /**
     * 处理单个入口/列表页：跟完全部分页并收集文章；下一层入口仅来自该页第一屏（避免分页重复扩层）。
     */
    private PageHarvest harvestListAndArticles(
            String entryUrl, String rootDomain, String normBase, EffectiveSiteCrawlPolicy policy)
            throws Exception {
        int maxPagination = policy.discovery().maxPaginationPerList();
        LinkedHashMap<String, String> articles = new LinkedHashMap<>();
        Set<String> paginationVisited = new HashSet<>();
        Deque<String> paginationQueue = new ArrayDeque<>();
        paginationQueue.add(entryUrl);
        Document firstPageDoc = null;

        while (!paginationQueue.isEmpty() && paginationVisited.size() < maxPagination) {
            String pageUrl = paginationQueue.poll();
            if (pageUrl == null || !paginationVisited.add(pageUrl)) {
                continue;
            }
            Document doc = fetchDocument(pageUrl, policy);
            if (firstPageDoc == null && pageUrl.equals(entryUrl)) {
                firstPageDoc = doc;
            }
            List<RagCrawlPageLink> links = extractPageLinks(doc, pageUrl);
            for (RagCrawlPageLink link : links) {
                String norm = RagWebCrawlUrlSupport.normalizeUrl(link.href());
                if (norm.isEmpty()
                        || !norm.startsWith("http")
                        || RagWebCrawlUrlSupport.isStaticResource(norm)
                        || !RagWebCrawlUrlSupport.isInRootDomain(norm, rootDomain)) {
                    continue;
                }
                if (RagArticleLinkHeuristics.looksLikeArticle(link)) {
                    String t = link.title() == null ? "" : link.title().trim();
                    articles.putIfAbsent(norm, t);
                    continue;
                }
                if (RagArticleLinkHeuristics.looksLikeNextPage(link)
                        || RagArticleLinkHeuristics.looksLikeLastPage(link)
                        || RagArticleLinkHeuristics.looksLikeNumericPageLink(link, entryUrl)) {
                    if (!paginationVisited.contains(norm) && !isExcludedCrawlUrl(norm)) {
                        paginationQueue.add(norm);
                    }
                }
            }
        }

        LinkedHashSet<String> nextLevel = new LinkedHashSet<>();
        if (firstPageDoc != null) {
            for (RagCrawlPageLink link : extractPageLinks(firstPageDoc, entryUrl)) {
                String norm = RagWebCrawlUrlSupport.normalizeUrl(link.href());
                if (norm.isEmpty()
                        || !RagWebCrawlUrlSupport.isInRootDomain(norm, rootDomain)
                        || RagWebCrawlUrlSupport.isStaticResource(norm)
                        || isExcludedCrawlUrl(norm)) {
                    continue;
                }
                if (RagArticleLinkHeuristics.looksLikeExploreTarget(link, normBase)) {
                    nextLevel.add(norm);
                }
            }
        }
        List<RagCrawlPageLink> articleLinks = new ArrayList<>(articles.size());
        for (var e : articles.entrySet()) {
            articleLinks.add(new RagCrawlPageLink(e.getKey(), e.getValue()));
        }
        return new PageHarvest(articleLinks, new ArrayList<>(nextLevel));
    }

    private Document fetchDocument(String url, EffectiveSiteCrawlPolicy policy) throws Exception {
        return discoveryPageFetcher.fetchHtmlDocument(url, policy);
    }

    public static List<RagCrawlPageLink> extractPageLinks(Document doc, String pageUrl) {
        LinkedHashMap<String, RagCrawlPageLink> merged = new LinkedHashMap<>();
        for (RagCrawlPageLink vsb : RagVsbListLinkSupport.extractListArticleLinks(doc, pageUrl)) {
            String norm = RagWebCrawlUrlSupport.normalizeUrl(vsb.href());
            if (!norm.isEmpty()) {
                merged.putIfAbsent(norm, new RagCrawlPageLink(norm, vsb.title()));
            }
        }
        Elements elements = doc.select("a[href], area[href], [data-href], [data-url], a[onclick]");
        for (Element el : elements) {
            String abs = RagCrawlLinkExtractSupport.extractHref(el, pageUrl);
            if (abs.isBlank()) {
                continue;
            }
            String title = RagCrawlLinkExtractSupport.linkTitle(el);
            merged.putIfAbsent(abs, new RagCrawlPageLink(abs, title));
        }
        Element relNext = doc.selectFirst("link[rel=next][href]");
        if (relNext != null) {
            String abs = relNext.absUrl("href");
            if (!abs.isBlank()) {
                merged.putIfAbsent(abs, new RagCrawlPageLink(abs, "下一页"));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** 登录、后台等明显非内容页。 */
    static boolean isExcludedCrawlUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        if (RagWebCrawlUrlSupport.isStaticResource(url)) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true;
        }
        return lower.matches(".*/(login|logout|signin|signout|register|cart|checkout)(/|$|\\?).*")
                || lower.contains("/wp-admin/")
                || lower.contains("/cgi-bin/")
                || lower.contains("javascript:");
    }

    private record PageHarvest(List<RagCrawlPageLink> articleLinks, List<String> nextLevelUrls) {}

    /** 供 {@code crawl_url_queue} EXPLORE 消费：单栏目/列表页跟分页并产出文章与下层入口。 */
    public ExplorePageHarvest harvestExplorePage(String exploreUrl, String normBase) throws Exception {
        return harvestExplorePage(exploreUrl, normBase, LEGACY_POLICY);
    }

    public ExplorePageHarvest harvestExplorePage(
            String exploreUrl, String normBase, EffectiveSiteCrawlPolicy policy) throws Exception {
        String rootDomain = RagWebCrawlUrlSupport.resolveRootDomain(normBase);
        PageHarvest harvest = harvestListAndArticles(exploreUrl, rootDomain, normBase, policy);
        return new ExplorePageHarvest(harvest.articleLinks(), harvest.nextLevelUrls());
    }

    public record ExplorePageHarvest(List<RagCrawlPageLink> articleLinks, List<String> nextExploreUrls) {}
}
