package com.aaron.cloud.rag;

import com.aaron.cloud.common.config.properties.AiRagProperties;
import com.aaron.cloud.rag.RagHtmlToMarkdown.ParsedPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/** 网页 HTML → 标题 + Markdown；支持 Jsoup 规则与 Readability。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagWebPageParseService {

    private final AiRagProperties aiRagProperties;

    public ParsedPage parse(String html, String sourceUrl, RagWebCrawlExtractConfig siteConfig) {
        if (html == null || html.isBlank()) {
            return new ParsedPage("", "");
        }
        RagWebCrawlExtractConfig cfg = siteConfig != null ? siteConfig : RagWebCrawlExtractConfig.empty();
        String mode = resolveExtractorMode(cfg);
        if ("readability".equalsIgnoreCase(mode)) {
            try {
                return parseWithReadability(html, sourceUrl, cfg);
            } catch (Exception e) {
                log.warn("Readability 解析失败，回退 Jsoup url={} err={}", sourceUrl, e.toString());
            }
        }
        return parseWithJsoup(html, sourceUrl, cfg);
    }

    private String resolveExtractorMode(RagWebCrawlExtractConfig cfg) {
        if (cfg.getExtractor() != null && !cfg.getExtractor().isBlank()) {
            return cfg.getExtractor().trim();
        }
        String global = aiRagProperties.getSiteCrawl().getContentExtractor();
        return global != null && !global.isBlank() ? global.trim() : "jsoup";
    }

    private ParsedPage parseWithReadability(String html, String sourceUrl, RagWebCrawlExtractConfig cfg) {
        String url = sourceUrl != null && !sourceUrl.isBlank() ? sourceUrl.trim() : "https://local.invalid/";
        Readability4J readability = new Readability4J(url, html);
        Article article = readability.parse();
        String title = article.getTitle() != null ? article.getTitle().trim() : "";
        String contentHtml = article.getContent();
        if (contentHtml == null || contentHtml.isBlank()) {
            return parseWithJsoup(html, sourceUrl, cfg);
        }
        Document d = Jsoup.parse(contentHtml, url);
        applyExcludeSelectors(d, cfg);
        Element root = pickRoot(d, cfg);
        String md = RagHtmlToMarkdown.buildMarkdownFromRoot(title, root != null ? root : d.body());
        if (md.isBlank()) {
            return parseWithJsoup(html, sourceUrl, cfg);
        }
        if (title.isBlank()) {
            title = RagHtmlToMarkdown.extractPageTitleFromDocument(Jsoup.parse(html, url), root);
        }
        return new ParsedPage(title, md);
    }

    private ParsedPage parseWithJsoup(String html, String sourceUrl, RagWebCrawlExtractConfig cfg) {
        String baseUri = sourceUrl != null && !sourceUrl.isBlank() ? sourceUrl.trim() : "";
        Document d = baseUri.isEmpty() ? Jsoup.parse(html) : Jsoup.parse(html, baseUri);
        d.select("script, style, noscript, iframe, svg").remove();
        applyExcludeSelectors(d, cfg);
        Element root = pickRoot(d, cfg);
        String pageTitle = RagHtmlToMarkdown.extractPageTitleFromDocument(d, root);
        if (cfg.getTitleSelector() != null && !cfg.getTitleSelector().isBlank()) {
            Element tEl = d.selectFirst(cfg.getTitleSelector().trim());
            if (tEl != null && !tEl.text().isBlank()) {
                pageTitle = RagHtmlToMarkdown.normalizeTitleStatic(tEl.text());
            }
        }
        String md = RagHtmlToMarkdown.buildMarkdownFromRoot(pageTitle, root != null ? root : d.body());
        return new ParsedPage(pageTitle, md);
    }

    private Element pickRoot(Document d, RagWebCrawlExtractConfig cfg) {
        if (cfg.getContentSelector() != null && !cfg.getContentSelector().isBlank()) {
            Elements found = d.select(cfg.getContentSelector().trim());
            if (!found.isEmpty()) {
                Element el = found.first();
                if (el != null && el.text().trim().length() > 20) {
                    return el;
                }
            }
        }
        return RagHtmlToMarkdown.pickContentRootStatic(d);
    }

    private void applyExcludeSelectors(Document d, RagWebCrawlExtractConfig cfg) {
        d.select("nav, header, footer, aside, .nav, .navbar, .footer, .sidebar, .breadcrumb").remove();
        if (cfg.getExcludeSelectors() == null) {
            return;
        }
        for (String sel : cfg.getExcludeSelectors()) {
            if (sel != null && !sel.isBlank()) {
                d.select(sel.trim()).remove();
            }
        }
    }
}
