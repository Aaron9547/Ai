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

    /** 文档展示名：Markdown H1 → 正文区标题 → 页面 title → 列表页标题 hint → URL。 */
    public static String resolveDocumentTitle(ParsedPage page, String sourceUrl) {
        return resolveDocumentTitle(page, sourceUrl, null);
    }

    public static String resolveDocumentTitle(ParsedPage page, String sourceUrl, String listTitleHint) {
        String fromMd = titleFromMarkdownLead(page != null ? page.markdown() : null);
        if (isMeaningfulArticleTitle(fromMd)) {
            return clampTitle(fromMd);
        }
        String hint = listTitleHint == null ? "" : listTitleHint.trim();
        if (isMeaningfulArticleTitle(hint) && !looksLikeGarbledText(hint)) {
            return clampTitle(hint);
        }
        String pageTitle = page != null && page.title() != null ? page.title().trim() : "";
        if (isMeaningfulArticleTitle(pageTitle)
                && !looksLikeSiteListTitle(pageTitle)
                && !looksLikeGarbledText(pageTitle)) {
            return clampTitle(normalizeTitleStatic(pageTitle));
        }
        if (!pageTitle.isBlank()) {
            String shortPart = shortTitleBeforeDash(pageTitle);
            if (isMeaningfulArticleTitle(shortPart)) {
                return clampTitle(shortPart);
            }
        }
        if (!fromMd.isBlank()) {
            return clampTitle(fromMd);
        }
        return clampTitle(fallbackFromUrl(sourceUrl));
    }

    public static String resolveDocumentTitle(String markdown, String sourceUrl) {
        return resolveDocumentTitle(new ParsedPage("", markdown), sourceUrl, null);
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
        return RagWebContentRootPicker.pick(d, RagWebCrawlExtractConfig.empty());
    }

    static String extractPageTitleFromDocument(Document d, Element contentRoot) {
        if (contentRoot != null) {
            for (String sel : new String[] {"h1", ".arti_title", ".news_title", ".article-title", "h2"}) {
                Element h = contentRoot.selectFirst(sel);
                if (h != null) {
                    String ht = h.text().trim();
                    if (isMeaningfulArticleTitle(ht)) {
                        return normalizeTitleStatic(ht);
                    }
                }
            }
        }
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

    /** @deprecated 请使用 {@link RagRichHtmlToMarkdown#buildRichMarkdown} */
    @Deprecated
    static String buildMarkdownFromRoot(String pageTitle, Element root) {
        if (root == null) {
            return "";
        }
        return RagRichHtmlToMarkdown.buildRichMarkdown(pageTitle, root, "");
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

    static boolean isMeaningfulArticleTitle(String title) {
        if (title == null) {
            return false;
        }
        String t = title.trim();
        return t.length() >= 2 && !t.matches("^\\d{1,4}$");
    }

    /** GBK 误按 UTF-8 解码后的典型乱码（如 Ã—Ã…、大量拉丁扩展）。 */
    static boolean looksLikeGarbledText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 2) {
            return false;
        }
        int bad = 0;
        int cjk = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            } else if (c == '\uFFFD' || (c >= 0x80 && c <= 0xFF)) {
                bad++;
            } else if (c == 'Ã' || c == 'Â' || c == 'Ð' || c == 'Ñ') {
                bad += 2;
            }
        }
        if (cjk >= 2 && bad == 0) {
            return false;
        }
        return bad >= 2 || (bad > 0 && cjk == 0 && t.length() <= 40);
    }

    static boolean looksLikeSiteListTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String t = title.trim();
        return t.contains("学院") && t.contains("-") && t.length() > 20
                || t.endsWith("首页") || t.contains("列表");
    }

    static String shortTitleBeforeDash(String title) {
        int dash = title.indexOf('-');
        if (dash > 0) {
            String left = title.substring(0, dash).trim();
            if (isMeaningfulArticleTitle(left)) {
                return left;
            }
        }
        int enDash = title.indexOf('—');
        if (enDash > 0) {
            String left = title.substring(0, enDash).trim();
            if (isMeaningfulArticleTitle(left)) {
                return left;
            }
        }
        return title.trim();
    }

    private static String clampTitle(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim();
        return t.length() > MAX_TITLE_LEN ? t.substring(0, MAX_TITLE_LEN) : t;
    }
}
