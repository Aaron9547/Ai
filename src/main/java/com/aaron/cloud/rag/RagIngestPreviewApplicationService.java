package com.aaron.cloud.rag;

import com.aaron.cloud.common.api.enums.RagChunkStrategy;
import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.common.rag.RagKnowledgeBaseRepository;
import com.aaron.cloud.common.rag.RagWebCrawlSiteRepository;
import com.aaron.cloud.common.rag.entity.RagKnowledgeBase;
import com.aaron.cloud.common.rag.entity.RagWebCrawlSite;
import com.aaron.cloud.rag.RagHtmlToMarkdown.ParsedPage;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewChunkView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewPageView;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewRequest;
import com.aaron.cloud.rag.dto.RagKbAdminDtos.ChunkPreviewView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public ChunkPreviewView preview(long tenantId, long kbId, ChunkPreviewRequest req) {
        RagKnowledgeBase kb = requireKb(tenantId, kbId);
        boolean hasUrl = req.getUrl() != null && !req.getUrl().isBlank();
        boolean hasBase = req.getBaseUrl() != null && !req.getBaseUrl().isBlank();
        if (hasUrl == hasBase) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provide exactly one of url or baseUrl");
        }
        RagWebCrawlExtractConfig extractConfig = resolveExtractConfig(tenantId, kbId, req);
        RagChunkStrategy strategy = resolveStrategy(kb, req.getChunkStrategy());
        int fixed = effectiveFixed(kb);
        int slide = effectiveSlide(kb);
        int maxChunks = aiRagProperties.getSiteCrawl().getPreviewMaxChunks();

        if (hasUrl) {
            ChunkPreviewPageView page = previewOneUrl(req.getUrl().trim(), extractConfig, strategy, fixed, slide, maxChunks);
            return new ChunkPreviewView(
                    strategy.getCode(),
                    fixed,
                    slide,
                    List.of(page),
                    1,
                    page.chunkCount());
        }

        int sampleN = Math.max(1, aiRagProperties.getSiteCrawl().getPreviewSiteSampleUrls());
        List<String> raw = linkDiscoveryService.discoverArticleLinks(req.getBaseUrl().trim(), req.getMaxDepth());
        List<String> urls = RagWebCrawlUrlSupport.sanitizeForCrawl(raw, req.getBaseUrl().trim());
        if (urls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no crawlable urls discovered");
        }
        List<String> sample = pickSample(urls, sampleN);
        List<ChunkPreviewPageView> pages = new ArrayList<>();
        int totalChunks = 0;
        for (String u : sample) {
            ChunkPreviewPageView p = previewOneUrl(u, extractConfig, strategy, fixed, slide, maxChunks);
            pages.add(p);
            totalChunks += p.chunkCount();
        }
        return new ChunkPreviewView(strategy.getCode(), fixed, slide, pages, pages.size(), totalChunks);
    }

    private ChunkPreviewPageView previewOneUrl(
            String url,
            RagWebCrawlExtractConfig extractConfig,
            RagChunkStrategy strategy,
            int fixed,
            int slide,
            int maxChunks) {
        int maxBytes = aiRagProperties.getSiteCrawl().getPreviewMaxBodyBytes();
        final RagHttpFetch.Fetched fetched;
        try {
            fetched = RagHttpFetch.get(url, maxBytes);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "fetch failed: " + url + " (" + e.getMessage() + ")", e);
        }
        String html = new String(fetched.body(), fetched.charset());
        ParsedPage page = webPageParseService.parse(html, url, extractConfig);
        String md = page.markdown();
        if (md.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty markdown after crawl: " + url);
        }
        String title = RagHtmlToMarkdown.resolveDocumentTitle(page, url);
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
        return new ChunkPreviewPageView(url, title, md.length(), parts.size(), truncated, chunks);
    }

    private static List<String> pickSample(List<String> urls, int n) {
        if (urls.size() <= n) {
            return urls;
        }
        List<String> out = new ArrayList<>();
        int step = Math.max(1, urls.size() / n);
        for (int i = 0; i < urls.size() && out.size() < n; i += step) {
            out.add(urls.get(i));
        }
        while (out.size() < n && out.size() < urls.size()) {
            String last = urls.get(urls.size() - 1);
            if (!out.contains(last)) {
                out.add(last);
            } else {
                break;
            }
        }
        return out;
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
