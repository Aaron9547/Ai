package com.aaron.cloud.rag;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** HTML 转近似 Markdown（标题与段落结构）；用于网页入库可读性与后续分片。 */
public final class RagHtmlToMarkdown {

    private RagHtmlToMarkdown() {}

    public static String toMarkdownish(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document d = Jsoup.parse(html);
        String title = d.title();
        StringBuilder md = new StringBuilder();
        if (title != null && !title.isBlank()) {
            md.append("# ").append(title.trim()).append("\n\n");
        }
        for (var el : d.select("h1, h2, h3, p, li")) {
            String txt = el.text().trim();
            if (!txt.isEmpty()) {
                md.append(txt).append("\n\n");
            }
        }
        if (md.length() < 24) {
            String body = d.body() != null ? d.body().text() : "";
            if (body != null && !body.isBlank()) {
                md.append(body.trim());
            }
        }
        return md.toString().trim();
    }
}
