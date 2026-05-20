package com.aaron.cloud.rag;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

/**
 * 网页正文 HTML → 富 Markdown（生产级，对齐并扩展 ly-ai {@code JsoupHelper#fetchPageAsMarkdown}）。
 *
 * <p>保留：图片绝对 URL、GFM 表格、超链接、标题层级、粗体/斜体、代码块；剔除门户导航壳层。
 */
public final class RagRichHtmlToMarkdown {

    private static final int MAX_CHARS = 500_000;

    /** 历史错误输出：{@code [\\!\\[alt\\](img)\\](outer)} */
    private static final Pattern BROKEN_LINKED_IMAGE =
            Pattern.compile("\\[\\\\!\\\\[([^\\]]*)\\\\]\\\\(([^)]+)\\\\)\\\\]\\\\(([^)]+)\\\\)");

    /** 历史错误输出：块级标题+段落被包进单个链接标签。 */
    private static final Pattern BLOCK_HEADING_WRAPPED_IN_LINK = Pattern.compile(
            "\\[((?:#{1,6})\\s[^\\n]+)\\n\\n([\\s\\S]*?)\\]\\(([^)]+)\\)", Pattern.MULTILINE);

    private RagRichHtmlToMarkdown() {}

    /** 去掉导航/页脚等壳层（须在正文根节点上调用）。 */
    public static void stripBoilerplate(Element root) {
        if (root == null) {
            return;
        }
        root.select(
                        "script, style, nav, footer, header, aside, iframe, noscript, svg, "
                                + "video, audio, object, embed, "
                                + "form, button, input, select, textarea, "
                                + "#header, .header, .site-header, .page-header, .topbar, .top-bar, #top, .head, .pagehead, "
                                + "#footer, .footer, .site-footer, .page-footer, #foot, .foot, .copyright, .icp-info, "
                                + "#sidebar, .sidebar, .side, .leftnav, .rightnav, .left-nav, .right-nav, "
                                + ".navbar, .nav-bar, .mainNav, .main-nav, #nav, .nav, .navigation, .topnav, .top-nav, "
                                + ".breadcrumb, .breadcrumbs, .menu, #menu, .dh, .linknav, "
                                + ".shortcut, .quick-links, .quick_link, .links-quick, .sitemap, #sitemap, "
                                + ".daohang, .dh_box, .navlist, #navlist, .friend-link, .friendlink, .flink, "
                                + "[role=navigation], [role=complementary]")
                .remove();
    }

    /**
     * 将正文根转为 Markdown；{@code pageTitle} 非空时前置 {@code # 标题}（与 ly-ai 输出一致，供 {@link RagHtmlToMarkdown#resolveDocumentTitle} 解析）。
     */
    public static String buildRichMarkdown(String pageTitle, Element root, String pageUrl) {
        if (root == null) {
            return pageTitle == null || pageTitle.isBlank() ? "" : "# " + pageTitle.trim() + "\n\n";
        }
        stripBoilerplate(root);
        String skipH1 = pageTitle == null ? "" : pageTitle.trim();
        String text = richContentToMarkdown(root, pageUrl, skipH1);
        if (text.isBlank()) {
            text = root.text() == null ? "" : root.text().trim();
        }
        text = normalizeMarkdownForStorage(text);
        text = repairBrokenMarkdownLinkSyntax(text);
        text = dedupeRepeatedParagraphs(text);
        text = dedupeHalvedIdenticalRun(text);
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS) + "\n\n...(truncated)";
        }
        StringBuilder out = new StringBuilder();
        if (pageTitle != null && !pageTitle.isBlank()) {
            out.append("# ").append(pageTitle.trim()).append("\n\n");
        }
        out.append(text);
        return out.toString().trim();
    }

    private static String richContentToMarkdown(Element root, String pageUrl, String skipDuplicateH1) {
        StringBuilder sb = new StringBuilder();
        appendSubtreeMarkdown(sb, root, pageUrl, skipDuplicateH1);
        String s = sb.toString().replace('\u00A0', ' ');
        s = s.replaceAll("[ \t]+\n", "\n");
        s = s.replaceAll("\n[ \t]+", "\n");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    private static void appendSubtreeMarkdown(StringBuilder sb, Element el, String pageUrl, String skipDuplicateH1) {
        String tag = el.normalName();
        if ("table".equals(tag)) {
            String md = tableToMarkdown(el, pageUrl);
            if (!md.isBlank()) {
                sb.append("\n\n").append(md).append("\n\n");
            }
            return;
        }
        if ("img".equals(tag)) {
            String md = imgToMarkdown(el, pageUrl);
            if (!md.isBlank()) {
                sb.append(md).append("\n");
            }
            return;
        }
        if ("br".equals(tag)) {
            sb.append("\n");
            return;
        }
        if ("hr".equals(tag)) {
            sb.append("\n---\n\n");
            return;
        }
        if (tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            int lvl = tag.charAt(1) - '0';
            StringBuilder inner = new StringBuilder();
            for (Node n : el.childNodes()) {
                appendNodeMarkdown(inner, n, pageUrl, skipDuplicateH1);
            }
            String t = inner.toString().trim();
            if (!t.isBlank() && !(lvl == 1 && t.equals(skipDuplicateH1))) {
                int mdLevel = Math.min(6, lvl + 1);
                sb.append("\n").append("#".repeat(mdLevel)).append(" ").append(t).append("\n\n");
            }
            return;
        }
        if ("strong".equals(tag) || "b".equals(tag)) {
            sb.append("**");
            for (Node n : el.childNodes()) {
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            sb.append("**");
            return;
        }
        if ("em".equals(tag) || "i".equals(tag)) {
            sb.append("*");
            for (Node n : el.childNodes()) {
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            sb.append("*");
            return;
        }
        if ("code".equals(tag) && el.parent() != null && !"pre".equals(el.parent().normalName())) {
            sb.append("`").append(el.text().trim()).append("`");
            return;
        }
        if ("p".equals(tag)) {
            StringBuilder inner = new StringBuilder();
            for (Node n : el.childNodes()) {
                appendNodeMarkdown(inner, n, pageUrl, skipDuplicateH1);
            }
            String t = inner.toString().trim();
            if (!t.isBlank()) {
                sb.append(t).append("\n\n");
            }
            return;
        }
        if ("li".equals(tag)) {
            sb.append("- ");
            for (Node n : el.childNodes()) {
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            sb.append("\n");
            return;
        }
        if ("ul".equals(tag) || "ol".equals(tag)) {
            for (Node n : el.childNodes()) {
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            sb.append("\n");
            return;
        }
        if ("div".equals(tag) || "section".equals(tag) || "article".equals(tag) || "blockquote".equals(tag)) {
            boolean wrote = false;
            for (Node n : el.childNodes()) {
                if (n instanceof TextNode tn) {
                    String raw = tn.getWholeText();
                    if (raw.isBlank()) {
                        continue;
                    }
                    if (wrote && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    sb.append(raw);
                    wrote = true;
                } else if (n instanceof Element child) {
                    if (wrote && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    appendNodeMarkdown(sb, child, pageUrl, skipDuplicateH1);
                    wrote = true;
                }
            }
            if (wrote) {
                sb.append("\n\n");
            }
            return;
        }
        if ("a".equals(tag)) {
            appendAnchorMarkdown(sb, el, pageUrl, skipDuplicateH1);
            return;
        }
        for (Node n : el.childNodes()) {
            if (n instanceof Element ce && !isPhrasingInlineTag(ce.normalName())) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
            }
            appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
        }
    }

    /**
     * 门户「专题卡片」常见结构：{@code <a><img/><h3>标题</h3><p>摘要</p></a>}。
     * 若整段包进一个链接并转义方括号，会得到 {@code [!\[\](img)](url)} 导致预览无法显示图片。
     */
    private static void appendAnchorMarkdown(
            StringBuilder sb, Element anchor, String pageUrl, String skipDuplicateH1) {
        String href = anchor.attr("href");
        if (href.isBlank()
                || href.toLowerCase(Locale.ROOT).startsWith("javascript:")
                || href.startsWith("#")) {
            for (Node n : anchor.childNodes()) {
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            return;
        }
        String abs = toAbsoluteUrl(pageUrl, anchor.hasAttr("href") ? anchor.absUrl("href") : href);
        if (abs.isBlank()) {
            for (Node n : anchor.childNodes()) {
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            return;
        }
        Element img = anchor.selectFirst("img");
        if (img != null) {
            String imgMd = imgToMarkdown(img, pageUrl);
            if (!imgMd.isBlank()) {
                sb.append("[").append(imgMd).append("](").append(abs).append(")\n\n");
            }
        }
        boolean hasBlockChild =
                !anchor.select("h1, h2, h3, h4, h5, h6, p, div, ul, ol, table").isEmpty();
        if (hasBlockChild) {
            for (Node n : anchor.childNodes()) {
                if (n instanceof Element child && "img".equals(child.normalName())) {
                    continue;
                }
                appendNodeMarkdown(sb, n, pageUrl, skipDuplicateH1);
            }
            return;
        }
        StringBuilder inner = new StringBuilder();
        for (Node n : anchor.childNodes()) {
            if (n instanceof Element child && "img".equals(child.normalName())) {
                continue;
            }
            appendNodeMarkdown(inner, n, pageUrl, skipDuplicateH1);
        }
        String linkText = inner.toString().trim();
        if (linkText.isBlank()) {
            return;
        }
        if (containsMarkdownLinkOrImage(linkText)) {
            sb.append(linkText);
            return;
        }
        sb.append("[")
                .append(escapePlainLinkLabel(linkText))
                .append("](")
                .append(abs)
                .append(")");
    }

    private static boolean containsMarkdownLinkOrImage(String text) {
        return text.contains("![") || text.matches(".*\\[[^\\]]+\\]\\([^)]+\\).*");
    }

    private static String escapePlainLinkLabel(String text) {
        return text.replace("[", "\\[").replace("]", "\\]");
    }

    private static void appendNodeMarkdown(StringBuilder sb, Node node, String pageUrl, String skipDuplicateH1) {
        if (node instanceof TextNode tn) {
            sb.append(tn.getWholeText());
        } else if (node instanceof Element el) {
            appendSubtreeMarkdown(sb, el, pageUrl, skipDuplicateH1);
        }
    }

    private static boolean isPhrasingInlineTag(String tag) {
        return switch (tag) {
            case "span", "a", "strong", "b", "em", "i", "u", "code", "font", "small", "sub", "sup", "mark", "del", "s" ->
                    true;
            default -> false;
        };
    }

    private static String imgToMarkdown(Element img, String pageUrl) {
        String raw = firstNonBlank(img.attr("src"), img.attr("data-src"), img.attr("data-original"), img.attr("data-url"));
        if (raw.isBlank()) {
            return "";
        }
        raw = raw.trim();
        if (raw.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return "";
        }
        String abs = img.hasAttr("src") && !img.attr("src").isBlank() ? img.absUrl("src") : toAbsoluteUrl(pageUrl, raw);
        if (abs.isBlank()) {
            abs = toAbsoluteUrl(pageUrl, raw);
        }
        if (abs.isBlank()) {
            return "";
        }
        String alt = img.attr("alt").replace("\n", " ").trim();
        alt = alt.replace("[", "\\[").replace("]", "\\]");
        return "![" + alt + "](" + abs + ")";
    }

    private static String tableToMarkdown(Element table, String pageUrl) {
        Elements rows = tableDirectRows(table);
        if (rows.isEmpty() || isLikelyLayoutTable(rows)) {
            return "";
        }
        List<List<String>> grid = new ArrayList<>();
        int maxCol = 0;
        for (Element tr : rows) {
            Elements cells = tr.select("> th, > td");
            if (cells.isEmpty()) {
                continue;
            }
            List<String> row = new ArrayList<>();
            for (Element cell : cells) {
                String v = cellInlineMarkdown(cell, pageUrl);
                v = v.replace('\r', ' ').replace('\n', ' ').replace("|", "\\|").trim();
                int cs = parseColspan(cell);
                row.add(v);
                for (int k = 1; k < cs; k++) {
                    row.add("");
                }
            }
            maxCol = Math.max(maxCol, row.size());
            grid.add(row);
        }
        if (grid.isEmpty()) {
            return "";
        }
        for (List<String> row : grid) {
            while (row.size() < maxCol) {
                row.add("");
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < grid.size(); r++) {
            sb.append("|");
            for (String c : grid.get(r)) {
                sb.append(" ").append(c.isEmpty() ? " " : c).append(" |");
            }
            sb.append("\n");
            if (r == 0) {
                sb.append("|");
                for (int i = 0; i < maxCol; i++) {
                    sb.append(" --- |");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static Elements tableDirectRows(Element table) {
        Elements rows = new Elements();
        for (Element ch : table.children()) {
            String n = ch.normalName();
            if ("tr".equals(n)) {
                rows.add(ch);
            } else if ("tbody".equals(n) || "thead".equals(n) || "tfoot".equals(n)) {
                for (Element ch2 : ch.children()) {
                    if ("tr".equals(ch2.normalName())) {
                        rows.add(ch2);
                    }
                }
            }
        }
        return rows;
    }

    private static String cellInlineMarkdown(Element cell, String pageUrl) {
        StringBuilder sb = new StringBuilder();
        for (Node n : cell.childNodes()) {
            if (n instanceof TextNode tn) {
                sb.append(tn.getWholeText());
            } else if (n instanceof Element e) {
                if ("img".equals(e.normalName())) {
                    sb.append(imgToMarkdown(e, pageUrl));
                } else if ("table".equals(e.normalName())) {
                    String nested = tableToMarkdown(e, pageUrl);
                    if (!nested.isBlank()) {
                        sb.append(nested.replace('\n', ' '));
                    }
                } else if ("br".equals(e.normalName())) {
                    sb.append(' ');
                } else if ("div".equals(e.normalName()) || "section".equals(e.normalName())) {
                    if (!sb.isEmpty() && !Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                        sb.append(' ');
                    }
                    sb.append(cellInlineMarkdown(e, pageUrl).trim());
                } else {
                    sb.append(cellInlineMarkdown(e, pageUrl));
                }
            }
        }
        return sb.toString();
    }

    private static boolean isLikelyLayoutTable(Elements rows) {
        int cellCount = 0;
        int linkCount = 0;
        int imgCount = 0;
        int emptyCells = 0;
        int textLen = 0;
        for (Element tr : rows) {
            Elements cells = tr.select("> th, > td");
            cellCount += cells.size();
            for (Element cell : cells) {
                linkCount += cell.select("a").size();
                imgCount += cell.select("img").size();
                String text = cell.text().trim();
                textLen += text.length();
                if (text.isBlank()) {
                    emptyCells++;
                }
            }
        }
        if (cellCount <= 0) {
            return true;
        }
        double emptyRatio = (double) emptyCells / (double) cellCount;
        if (cellCount >= 20 && (linkCount >= 10 || imgCount >= 6)) {
            return true;
        }
        if (cellCount >= 12 && emptyRatio >= 0.35d) {
            return true;
        }
        if (textLen < 220 && linkCount >= 8) {
            return true;
        }
        return rows.size() <= 2 && cellCount >= 6 && linkCount >= 4;
    }

    private static int parseColspan(Element cell) {
        String c = cell.attr("colspan");
        if (c.isBlank()) {
            return 1;
        }
        try {
            int v = Integer.parseInt(c.trim());
            return Math.max(1, Math.min(v, 20));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String toAbsoluteUrl(String pageUrl, String rawLink) {
        if (rawLink == null || rawLink.isBlank()) {
            return "";
        }
        String trimmed = rawLink.trim();
        if (trimmed.startsWith("//")) {
            try {
                URI base = URI.create(pageUrl);
                return base.getScheme() + ":" + trimmed;
            } catch (Exception e) {
                return "https:" + trimmed;
            }
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            return URI.create(pageUrl).resolve(trimmed).toString();
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /**
     * 修复旧版 HTML→MD 对 {@code <a><img/></a>} 与 {@code <a><div><h2/><p/></div></a>} 的错误转义/包裹。
     * 央广网 {@code /zhuanti/news/} 等门户为「图链」「文链」两个独立 {@code <a>}，新逻辑已分流处理。
     */
    static String repairBrokenMarkdownLinkSyntax(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.contains("\\!\\[")) {
            text = BROKEN_LINKED_IMAGE.matcher(text).replaceAll("[![$1]($2)]($3)");
        }
        if (text.indexOf('\n') >= 0 && text.contains("](")) {
            text = BLOCK_HEADING_WRAPPED_IN_LINK.matcher(text).replaceAll("$1\n\n$2");
        }
        return text;
    }

    private static String normalizeMarkdownForStorage(String text) {
        if (text.isBlank()) {
            return text;
        }
        text = text.replace('\u00A0', ' ');
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i].replaceAll("[ \t\\x0B\\f]{2,}", " "));
        }
        text = sb.toString();
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    private static String dedupeRepeatedParagraphs(String text) {
        if (text.isBlank()) {
            return text;
        }
        String[] paras = text.split("\\n{2,}");
        if (paras.length < 2) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (String p : paras) {
            String t = p.trim();
            if (t.length() > 120 && t.equals(prev)) {
                continue;
            }
            prev = t;
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(t);
        }
        return sb.toString();
    }

    private static String dedupeHalvedIdenticalRun(String text) {
        if (text.isBlank() || text.length() < 1500) {
            return text;
        }
        String norm = text.replaceAll("\\s+", " ").trim();
        int mid = norm.length() / 2;
        if (mid < 600) {
            return text;
        }
        String first = norm.substring(0, mid).trim();
        String second = norm.substring(mid).trim();
        if (first.equals(second)) {
            return first;
        }
        return text;
    }
}
