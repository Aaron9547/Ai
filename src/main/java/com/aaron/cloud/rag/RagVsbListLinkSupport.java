package com.aaron.cloud.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 解析高校 VSB/Visual SiteBuilder 图文列表页：文章 URL 常在 {@code display:none} 的空 {@code <a>} 中，
 * 标题在页面脚本 {@code var u_u*_title =[...]} 与 {@code u_u*_id} 数组里（见 tuwen 列表模板）。
 */
public final class RagVsbListLinkSupport {

    private static final Pattern TITLE_ARRAY =
            Pattern.compile("var\\s+(\\w+)_title\\s*=\\s*\\[([^\\]]*)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_ARRAY =
            Pattern.compile("var\\s+(\\w+)_id\\s*=\\s*\\[([^\\]]*)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_STRING = Pattern.compile("\"([^\"]*)\"");

    private RagVsbListLinkSupport() {}

    /** 从 VSB 列表页脚本与隐藏锚点提取带标题的文章链接。 */
    public static List<RagCrawlPageLink> extractListArticleLinks(Document doc, String pageUrl) {
        if (doc == null) {
            return List.of();
        }
        Map<String, String> idToTitle = parseIdTitlePairs(doc);
        LinkedHashMap<String, RagCrawlPageLink> byHref = new LinkedHashMap<>();
        for (Element a : doc.select("a[href][id]")) {
            String id = a.id();
            if (id == null || !id.matches("u_u\\d+_\\d+_a")) {
                continue;
            }
            String abs = a.absUrl("href");
            if (abs.isBlank()) {
                continue;
            }
            String title = titleForAnchorId(id, idToTitle);
            if (title.isBlank()) {
                title = anchorText(a);
            }
            byHref.putIfAbsent(abs, new RagCrawlPageLink(abs, title));
        }
        return new ArrayList<>(byHref.values());
    }

    private static String titleForAnchorId(String anchorId, Map<String, String> idToTitle) {
        Matcher m = Pattern.compile("u_u\\d+_(\\d+)_a").matcher(anchorId);
        if (m.find()) {
            return idToTitle.getOrDefault(m.group(1), "");
        }
        return "";
    }

    private static String anchorText(Element a) {
        String t = a.hasAttr("title") && !a.attr("title").isBlank() ? a.attr("title") : a.text();
        return t == null ? "" : t.trim();
    }

    private static Map<String, String> parseIdTitlePairs(Document doc) {
        Map<String, List<String>> prefixTitles = new LinkedHashMap<>();
        Map<String, List<String>> prefixIds = new LinkedHashMap<>();
        String scriptBlob = doc.select("script").eachText().stream()
                .filter(s -> s.contains("_title") && s.contains("_id"))
                .reduce("", String::concat);
        if (scriptBlob.isEmpty()) {
            return Map.of();
        }
        Matcher tm = TITLE_ARRAY.matcher(scriptBlob);
        while (tm.find()) {
            prefixTitles.put(tm.group(1).toLowerCase(Locale.ROOT), parseQuotedStrings(tm.group(2)));
        }
        Matcher im = ID_ARRAY.matcher(scriptBlob);
        while (im.find()) {
            prefixIds.put(im.group(1).toLowerCase(Locale.ROOT), parseQuotedStrings(im.group(2)));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String prefix : prefixTitles.keySet()) {
            List<String> titles = prefixTitles.get(prefix);
            List<String> ids = prefixIds.get(prefix);
            if (titles == null || ids == null) {
                continue;
            }
            int n = Math.min(titles.size(), ids.size());
            for (int i = 0; i < n; i++) {
                String id = ids.get(i).trim();
                String title = titles.get(i).trim();
                if (!id.isEmpty() && title.length() >= 2) {
                    out.putIfAbsent(id, title);
                }
            }
        }
        return out;
    }

    private static List<String> parseQuotedStrings(String arrayBody) {
        List<String> out = new ArrayList<>();
        Matcher m = QUOTED_STRING.matcher(arrayBody);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }
}
