package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagChunkStrategy;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.rag.RagHtmlToMarkdown.ParsedPage;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewChunkView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewPageView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewRequest;
import com.aaron.cloud.rag.crawl.fetch.HttpFetcher;
import com.aaron.cloud.rag.crawl.fetch.PolitenessGate;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.aaron.cloud.rag.crawl.policy.SiteCrawlPolicyResolver;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIngestPreviewApplicationService {

    private static final int PREVIEW_SNIPPET_CHARS = 280;

    private final RagKnowledgeBaseRepository ragKnowledgeBaseRepository;
    private final RagWebCrawlSiteRepository webCrawlSiteRepository;
    private final RagWebPageParseService webPageParseService;
    private final RagWebCrawlExtractConfigSupport extractConfigSupport;
    private final RagSiteLinkDiscoveryService linkDiscoveryService;
    private final AiRagProperties aiRagProperties;
    private final SiteCrawlPolicyResolver siteCrawlPolicyResolver;
    private final HttpFetcher httpFetcher;
    private final PolitenessGate politenessGate;

    public ChunkPreviewView preview(long tenantId, long kbId, ChunkPreviewRequest req) {
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        boolean hasUrl = req.getUrl() != null && !req.getUrl().isBlank();
        boolean hasBase = req.getBaseUrl() != null && !req.getBaseUrl().isBlank();
        if (hasUrl == hasBase) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provide exactly one of url or baseUrl");
        }
        RagWebCrawlExtractConfig extractConfig = resolveExtractConfig(tenantId, kbId, req);
        EffectiveSiteCrawlPolicy crawlPolicy = siteCrawlPolicyResolver.resolve(tenantId, extractConfig);
        RagChunkStrategy strategy = resolveStrategy(kb, req.getChunkStrategy());
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        int maxChunks = aiRagProperties.getSiteCrawl().getPreviewMaxChunks();

        if (hasUrl) {
            ChunkPreviewPageView page =
                    previewOneUrl(req.getUrl().trim(), extractConfig, crawlPolicy, strategy, fixed, slide, maxChunks);
            return new ChunkPreviewView(
                    strategy.getCode(),
                    fixed,
                    slide,
                    List.of(page),
                    1,
                    page.chunkCount(),
                    1);
        }

        String base = req.getBaseUrl().trim();
        int sampleN = Math.max(1, aiRagProperties.getSiteCrawl().getPreviewSiteSampleUrls());
        List<String> raw = linkDiscoveryService.discoverSiteUrls(base, req.getMaxDepth());
        List<String> urls = RagWebCrawlUrlSupport.sanitizeForCrawl(raw, base);
        if (urls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no crawlable urls discovered");
        }
        List<String> sample = pickPreviewSample(urls, sampleN, base);
        log.info(
                "站点分片预览抽样 baseUrl={} maxDepth={} discovered={} sample={}",
                base,
                req.getMaxDepth(),
                urls.size(),
                sample);
        List<ChunkPreviewPageView> pages = new ArrayList<>();
        int totalChunks = 0;
        for (String u : sample) {
            try {
                ChunkPreviewPageView p = previewOneUrl(u, extractConfig, crawlPolicy, strategy, fixed, slide, maxChunks);
                pages.add(p);
                totalChunks += p.chunkCount();
            } catch (ResponseStatusException e) {
                pages.add(failedPreviewPage(u, e.getReason()));
                log.warn("站点分片预览单页失败 url={} reason={}", u, e.getReason());
            } catch (Exception e) {
                pages.add(failedPreviewPage(u, e.getMessage()));
                log.warn("站点分片预览单页失败 url={} err={}", u, e.toString());
            }
        }
        return new ChunkPreviewView(
                strategy.getCode(), fixed, slide, pages, pages.size(), totalChunks, urls.size());
    }

    /**
     * 站点预览抽样：优先非首页的内页（列表/文章），最多 {@code n} 条；发现数不足时全部预览。
     */
    static List<String> pickPreviewSample(List<String> urls, int n, String baseUrl) {
        if (urls == null || urls.isEmpty() || n < 1) {
            return List.of();
        }
        if (urls.size() <= n) {
            return new ArrayList<>(urls);
        }
        String normBase = RagWebCrawlUrlSupport.normalizeUrl(baseUrl);
        List<String> inner = new ArrayList<>();
        for (String u : urls) {
            if (u != null && !u.equals(normBase)) {
                inner.add(u);
            }
        }
        List<String> pool = inner.size() >= n ? inner : new ArrayList<>(urls);
        return pickEvenly(pool, n);
    }

    private static List<String> pickEvenly(List<String> urls, int n) {
        List<String> out = new ArrayList<>(n);
        int step = Math.max(1, urls.size() / n);
        for (int i = 0; i < urls.size() && out.size() < n; i += step) {
            out.add(urls.get(i));
        }
        for (int i = urls.size() - 1; i >= 0 && out.size() < n; i--) {
            String u = urls.get(i);
            if (!out.contains(u)) {
                out.add(u);
            }
        }
        return out;
    }

    private static ChunkPreviewPageView failedPreviewPage(String url, String message) {
        String msg = message != null && !message.isBlank() ? message : "preview failed";
        return new ChunkPreviewPageView(url, "预览失败", 0, 0, false, List.of(), msg);
    }

    private ChunkPreviewPageView previewOneUrl(
            String url,
            RagWebCrawlExtractConfig extractConfig,
            EffectiveSiteCrawlPolicy crawlPolicy,
            RagChunkStrategy strategy,
            int fixed,
            int slide,
            int maxChunks) {
        int maxBytes =
                Math.min(
                        crawlPolicy.fetch().maxBodyBytes(),
                        aiRagProperties.getSiteCrawl().getPreviewMaxBodyBytes());
        EffectiveSiteCrawlPolicy.FetchPolicy fetchPolicy =
                new EffectiveSiteCrawlPolicy.FetchPolicy(
                        maxBytes,
                        crawlPolicy.fetch().timeoutMs(),
                        crawlPolicy.fetch().metaRefreshMaxHops(),
                        crawlPolicy.fetch().conditionalRequest(),
                        crawlPolicy.fetch().sharedHttpClient());
        politenessGate.configure(crawlPolicy.politeness());
        String html;
        try {
            politenessGate.acquire(url, crawlPolicy.politeness());
            try {
                var fetched =
                        httpFetcher.fetch(
                                url,
                                fetchPolicy,
                                siteReferer(url),
                                null,
                                null);
                html = new String(fetched.body(), fetched.charset());
            } finally {
                politenessGate.release();
            }
        } catch (PolitenessGate.RobotsDisallowedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "robots disallow: " + url);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "fetch failed: " + url + " (" + e.getMessage() + ")", e);
        }
        ParsedPage page = webPageParseService.parse(html, url, extractConfig);
        String md = page.markdown();
        if (md.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty markdown after crawl: " + url);
        }
        String title =
                page.title() != null && !page.title().isBlank()
                        ? page.title().trim()
                        : RagHtmlToMarkdown.resolveDocumentTitle(page, url);
        List<String> parts = RagChunkSplitter.split(md, strategy, fixed, slide);
        List<ChunkPreviewChunkView> chunks = new ArrayList<>();
        int seq = 0;
        for (String part : parts) {
            if (seq >= maxChunks) {
                break;
            }
            String plain = part.stripLeading();
            chunks.add(new ChunkPreviewChunkView(seq, part.length(), snippet(plain)));
            seq++;
        }
        boolean truncated = parts.size() > maxChunks;
        return new ChunkPreviewPageView(url, title, md.length(), parts.size(), truncated, chunks, null);
    }

    private RagWebCrawlExtractConfig resolveExtractConfig(long tenantId, long kbId, ChunkPreviewRequest req) {
        if (req.getExtractConfig() != null) {
            return req.getExtractConfig();
        }
        if (req.getSiteId() != null) {
            return webCrawlSiteRepository
                    .findById(tenantId, kbId, req.getSiteId())
                    .map(s -> extractConfigSupport.fromJson(s.getExtractConfig()))
                    .orElse(RagWebCrawlExtractConfig.empty());
        }
        return RagWebCrawlExtractConfig.empty();
    }

    private static RagChunkStrategy resolveStrategy(RagKnowledgeBase kb, Integer code) {
        if (code != null) {
            return RagChunkStrategy.fromCode(code);
        }
        return kb.getDefaultChunkStrategy() != null ? kb.getDefaultChunkStrategy() : RagChunkStrategy.SEMANTIC;
    }

    private static int effectiveFixed(RagKnowledgeBase kb) {
        return kb.getChunkFixedChars() != null && kb.getChunkFixedChars() > 0 ? kb.getChunkFixedChars() : 1000;
    }

    private static int effectiveSlide(RagKnowledgeBase kb) {
        return kb.getChunkSlideOverlap() != null && kb.getChunkSlideOverlap() >= 0 ? kb.getChunkSlideOverlap() : 120;
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

    private RagKnowledgeBase requireKb(long tenantId, long kbId) {
        RagKnowledgeBase kb = ragKnowledgeBaseRepository.findByIdAndTenant(kbId, tenantId);
        if (kb == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found");
        }
        return kb;
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\r', ' ').replace('\n', ' ').trim();
        return t.length() > PREVIEW_SNIPPET_CHARS ? t.substring(0, PREVIEW_SNIPPET_CHARS) + "…" : t;
    }
}
