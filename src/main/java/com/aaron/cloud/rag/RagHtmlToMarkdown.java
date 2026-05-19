package com.aaron.cloud.rag;

import java.net.URI;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** HTML 转近似 Markdown（标题与段落结构）；正文抽取见 {@link RagWebPageParseService}。 */
public final class RagHtmlToMarkdown {

    private static final int MAX_TITLE_LEN = 512;

    private RagHtmlToMarkdown() {}

    /** 解析结果：页面标题 + 正文 Markdown。 */
    public record ParsedPage(String title, String markdown) {}

    /** 文档展示名：页面标题 → Markdown 首行 H1 → URL 末段 → 主机名。 */
    public static String resolveDocumentTitle(ParsedPage page, String sourceUrl) {
        if (page != null && page.title() != null && !page.title().isBlank()) {
            return clampTitle(page.title().trim());
        }
        String fromMd = titleFromMarkdownLead(page != null ? page.markdown() : null);
        if (!fromMd.isBlank()) {
            return clampTitle(fromMd);
        }
        return clampTitle(fallbackFromUrl(sourceUrl));
    }

    public static String resolveDocumentTitle(String markdown, String sourceUrl) {
        return resolveDocumentTitle(new ParsedPage("", markdown), sourceUrl);
    }

    static String titleFromMarkdownLead(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String line = markdown.stripLeading();
        if (!line.startsWith("#")) {
            return "";
        }
        int nl = line.indexOf('\n');
        String head = nl >= 0 ? line.substring(0, nl) : line;
        return head.replaceFirst("^#+\\s*", "").trim();
    }

    static Element pickContentRootStatic(Document d) {
        for (String sel :
                new String[] {
                    "article",
                    "main",
                    "[role=main]",
                    "#content",
                    ".content",
                    ".article-content",
                    ".post-content",
                    ".entry-content"
                }) {
            Elements found = d.select(sel);
            if (!found.isEmpty()) {
                Element el = found.first();
                if (el != null && el.text().trim().length() > 80) {
                    return el;
                }
            }
        }
        return d.body();
    }

    static String extractPageTitleFromDocument(Document d, Element contentRoot) {
        String t = d.title();
        if (t != null && !t.isBlank()) {
            return normalizeTitleStatic(t);
        }
        Element og = d.selectFirst("meta[property=og:title], meta[name=og:title]");
        if (og != null) {
            String c = og.attr("content");
            if (c != null && !c.isBlank()) {
                return normalizeTitleStatic(c);
            }
        }
        Element tw = d.selectFirst("meta[name=twitter:title]");
        if (tw != null) {
            String c = tw.attr("content");
            if (c != null && !c.isBlank()) {
                return normalizeTitleStatic(c);
            }
        }
        if (contentRoot != null) {
            Element h1 = contentRoot.selectFirst("h1");
            if (h1 != null) {
                String h = h1.text().trim();
                if (!h.isBlank()) {
                    return normalizeTitleStatic(h);
                }
            }
        }
        return "";
    }

    static String buildMarkdownFromRoot(String pageTitle, Element root) {
        if (root == null) {
            return "";
        }
        StringBuilder md = new StringBuilder();
        if (pageTitle != null && !pageTitle.isBlank()) {
            md.append("# ").append(pageTitle.trim()).append("\n\n");
        }
        for (Element el : root.select("h1, h2, h3, h4, p, li, blockquote, pre")) {
            String tag = el.tagName();
            String txt = el.text().trim();
            if (txt.isEmpty()) {
                continue;
            }
            if ("h1".equals(tag) && pageTitle != null && txt.equals(pageTitle.trim())) {
                continue;
            }
            switch (tag) {
                case "h1" -> md.append("# ").append(txt).append("\n\n");
                case "h2" -> md.append("## ").append(txt).append("\n\n");
                case "h3" -> md.append("### ").append(txt).append("\n\n");
                case "h4" -> md.append("#### ").append(txt).append("\n\n");
                case "li" -> md.append("- ").append(txt).append("\n");
                case "blockquote" -> md.append("> ").append(txt).append("\n\n");
                case "pre" -> md.append("```\n").append(txt).append("\n```\n\n");
                default -> md.append(txt).append("\n\n");
            }
        }
        if (md.length() < 48) {
            String body = root.text();
            if (body != null && !body.isBlank()) {
                md.append(body.trim());
            }
        }
        return md.toString().trim();
    }

    static String normalizeTitleStatic(String raw) {
        String t = raw.trim().replaceAll("\\s+", " ");
        int pipe = t.indexOf('|');
        if (pipe > 0 && pipe < t.length() - 1) {
            String left = t.substring(0, pipe).trim();
            if (!left.isBlank()) {
                t = left;
            }
        }
        return t;
    }

    private static String fallbackFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "web-page";
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost();
            if (host != null && !host.isBlank()) {
                String path = u.getPath();
                if (path != null && path.length() > 1) {
                    String seg = path.substring(path.lastIndexOf('/') + 1);
                    if (!seg.isBlank() && !seg.equals("/")) {
                        return host + " / " + seg;
                    }
                }
                return host;
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return url.length() > 200 ? url.substring(0, 200) : url;
    }

    private static String clampTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        return t.length() > MAX_TITLE_LEN ? t.substring(0, MAX_TITLE_LEN) : t;
    }
}
