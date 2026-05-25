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
import org.springframework.stereotype.Service;

/**
 * 网页正文解析唯一入口：高校 CMS 正文根 + 富 Markdown（图片/表格/链接），无简化版回退。
 *
 * <p>对齐 ly-ai {@code JsoupHelper#fetchPageAsMarkdown} + {@code LocalArticleLinksWebCrawlService} 入库链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagWebPageParseService {

    private static final int MIN_USEFUL_MARKDOWN_LEN = 48;

    private final AiRagProperties aiRagProperties;

    public ParsedPage parse(String html, String sourceUrl, RagWebCrawlExtractConfig siteConfig) {
        return parse(html, sourceUrl, siteConfig, null);
    }

    /**
     * @param listTitleHint 列表页已解析出的文章标题（VSB 图文列表等），用于补全文档名
     */
    public ParsedPage parse(
            String html, String sourceUrl, RagWebCrawlExtractConfig siteConfig, String listTitleHint) {
        if (html == null || html.isBlank()) {
            return new ParsedPage("", "");
        }
        RagWebCrawlExtractConfig cfg = siteConfig != null ? siteConfig : RagWebCrawlExtractConfig.empty();
        String baseUri =
                sourceUrl != null && !sourceUrl.isBlank() ? sourceUrl.trim() : "https://local.invalid/";
        Document doc = Jsoup.parse(html, baseUri);
        doc.select("script, style, noscript, iframe, svg").remove();
        applyExcludeSelectors(doc, cfg);

        String pageTitle = resolvePageTitle(doc, cfg);
        String md = renderPrimaryMarkdown(doc, cfg, pageTitle, baseUri);

        if (md.length() < MIN_USEFUL_MARKDOWN_LEN && useReadabilityFallback(cfg)) {
            String readabilityMd = renderReadabilityMarkdown(html, baseUri, pageTitle);
            if (readabilityMd.length() > md.length()) {
                md = readabilityMd;
            }
        }

        if (md.isBlank()) {
            log.warn("网页正文 Markdown 为空 url={}（可能为跳转壳/纯首页/需登录）", baseUri);
            return new ParsedPage(
                    RagHtmlToMarkdown.resolveDocumentTitle(
                            new ParsedPage(pageTitle, ""), baseUri, listTitleHint),
                    "");
        }

        String documentTitle =
                RagHtmlToMarkdown.resolveDocumentTitle(new ParsedPage(pageTitle, md), baseUri, listTitleHint);
        return new ParsedPage(documentTitle, md);
    }

    private String renderPrimaryMarkdown(
            Document doc, RagWebCrawlExtractConfig cfg, String pageTitle, String baseUri) {
        Element root = RagWebContentRootPicker.pick(doc, cfg);
        String md = "";
        if (root != null) {
            md = RagRichHtmlToMarkdown.buildRichMarkdown(pageTitle, root.clone(), baseUri);
        }
        if (md.length() < MIN_USEFUL_MARKDOWN_LEN && doc.body() != null) {
            String bodyMd =
                    RagRichHtmlToMarkdown.buildRichMarkdown(pageTitle, doc.body().clone(), baseUri);
            if (bodyMd.length() > md.length()) {
                md = bodyMd;
            }
        }
        return md;
    }

    private String renderReadabilityMarkdown(String html, String baseUri, String pageTitle) {
        try {
            Readability4J readability = new Readability4J(baseUri, html);
            Article article = readability.parse();
            String contentHtml = article.getContent();
            if (contentHtml == null || contentHtml.isBlank()) {
                return "";
            }
            String title =
                    article.getTitle() != null && !article.getTitle().isBlank()
                            ? article.getTitle().trim()
                            : pageTitle;
            Document fragment = Jsoup.parse(contentHtml, baseUri);
            Element root = fragment.body();
            return RagRichHtmlToMarkdown.buildRichMarkdown(title, root.clone(), baseUri);
        } catch (Exception e) {
            log.debug("Readability 富转换失败 url={} err={}", baseUri, e.toString());
            return "";
        }
    }

    private String resolvePageTitle(Document doc, RagWebCrawlExtractConfig cfg) {
        Element root = RagWebContentRootPicker.pick(doc, cfg);
        String pageTitle = RagHtmlToMarkdown.extractPageTitleFromDocument(doc, root);
        if (cfg.getTitleSelector() != null && !cfg.getTitleSelector().isBlank()) {
            Element tEl = doc.selectFirst(cfg.getTitleSelector().trim());
            if (tEl != null && !tEl.text().isBlank()) {
                pageTitle = RagHtmlToMarkdown.normalizeTitleStatic(tEl.text());
            }
        }
        return pageTitle;
    }

    private boolean useReadabilityFallback(RagWebCrawlExtractConfig cfg) {
        String mode = cfg.getExtractor();
        if (mode != null && !mode.isBlank()) {
            return "readability".equalsIgnoreCase(mode.trim());
        }
        String global = aiRagProperties.getSiteCrawl().getContentExtractor();
        return global != null && "readability".equalsIgnoreCase(global.trim());
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
