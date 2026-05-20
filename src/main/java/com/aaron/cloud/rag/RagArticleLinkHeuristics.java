package com.aaron.cloud.rag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 对齐 ly-ai-application {@code BatchCrawlHandler#looksLikeArticle} 的本地文章/翻页判断。 */
public final class RagArticleLinkHeuristics {

    private static final Set<String> ARTICLE_PATH_KEYWORDS =
            Set.of(
                    "/news", "/article", "/content", "/info", "/detail", "/show", "/view",
                    "/xw", "/xwzx", "xwdt", "/tzgg", "/gg/", "/ggfw", "/notice", "/announce", "/bulletin",
                    "/zyxw", "/mtxw", "/xyxw", "/zxzx", "/ggtz", "/tztg", "/display", "/read", "/mtbd");

    private static final Set<String> NON_ARTICLE_TITLE_WORDS =
            Set.of(
                    "首页", "上一页", "下一页", "下页", "尾页", "末页", "更多", "more", "返回", "关闭", "搜索", "登录", "注册",
                    "english", "en", "设为首页", "加入收藏");

    private RagArticleLinkHeuristics() {}

    public static boolean looksLikeArticle(RagCrawlPageLink item) {
        if (item == null || item.href() == null || item.href().isBlank()) {
            return false;
        }
        String href = item.href().trim().toLowerCase(Locale.ROOT);
        String title = item.title() == null ? "" : item.title().trim();
        if (href.startsWith("javascript:") || "#".equals(href)) {
            return false;
        }
        if (href.endsWith("/index.shtml")
                || href.endsWith("/index.html")
                || href.endsWith("/index.htm")
                || href.matches(".*/index\\.(s?html?|jsp|aspx?|php)(\\?.*)?$")) {
            return false;
        }
        if (title.length() < 2 && !looksLikeStrongArticleUrl(href)) {
            return false;
        }
        String titleLower = title.toLowerCase(Locale.ROOT);
        if (NON_ARTICLE_TITLE_WORDS.contains(titleLower)) {
            return false;
        }
        if (title.matches("^\\d{1,4}$")) {
            return false;
        }
        boolean pathLikeArticle = ARTICLE_PATH_KEYWORDS.stream().anyMatch(href::contains);
        boolean hasDate = href.matches(".*\\d{4}[-/]?\\d{1,2}[-/]?\\d{1,2}.*");
        boolean hasNumericId = href.matches(".*[\\/_-]\\d{5,}([./].*)?$");
        boolean hasArticleSuffix = href.matches(".*\\.(s?html?|aspx?|php|jsp)(\\?.*)?$");
        boolean hasIdParam = href.matches(".*[?&](id|articleId|newsId|contentId)=\\d+.*");
        return pathLikeArticle || hasDate || hasNumericId || hasArticleSuffix || hasIdParam;
    }

    public static boolean looksLikeNextPage(RagCrawlPageLink item) {
        if (item == null || item.title() == null) {
            return false;
        }
        String t = item.title().trim().toLowerCase(Locale.ROOT);
        return t.equals("下一页")
                || t.equals("下页")
                || t.contains("next page")
                || t.equals("next")
                || t.equals("»")
                || t.equals(">>")
                || t.equals("›");
    }

    public static boolean looksLikeLastPage(RagCrawlPageLink item) {
        if (item == null || item.title() == null) {
            return false;
        }
        String t = item.title().trim();
        return "尾页".equals(t) || "末页".equals(t) || "最后一页".equals(t);
    }

    /** 列表页码数字链接（同栏目分页序列）。 */
    public static boolean looksLikeNumericPageLink(RagCrawlPageLink item, String currentListUrl) {
        if (item == null || item.title() == null || item.href() == null) {
            return false;
        }
        String title = item.title().trim();
        if (!title.matches("\\d{1,4}")) {
            return false;
        }
        return sharesListPathPrefix(currentListUrl, item.href());
    }

    public static boolean isPaginationOrNav(RagCrawlPageLink item) {
        return looksLikeNextPage(item) || looksLikeLastPage(item);
    }

    /** 是否可作为下一层 BFS 探索入口（栏目/列表，非文章详情）。 */
    public static boolean looksLikeExploreTarget(RagCrawlPageLink item, String normBase) {
        if (item == null || item.href() == null || item.href().isBlank()) {
            return false;
        }
        if (looksLikeArticle(item) || isPaginationOrNav(item)) {
            return false;
        }
        String href = RagWebCrawlUrlSupport.normalizeUrl(item.href());
        if (href.isEmpty() || href.equals(normBase)) {
            return false;
        }
        String title = item.title() == null ? "" : item.title().trim().toLowerCase(Locale.ROOT);
        if (title.isEmpty() || NON_ARTICLE_TITLE_WORDS.contains(title)) {
            return false;
        }
        return true;
    }

    /** VSB 等 CMS 列表页隐藏锚点无文字，但 {@code /info/{col}/{id}.htm} 为详情页。 */
    public static boolean looksLikeStrongArticleUrl(String href) {
        if (href == null || href.isBlank()) {
            return false;
        }
        String h = href.toLowerCase(Locale.ROOT);
        return h.matches(".*/info/\\d+/\\d+\\.htm(?:\\?.*)?$")
                || h.matches(".*/content\\.jsp\\?.*(?:id|contentid)=\\d+.*");
    }

    private static boolean sharesListPathPrefix(String currentListUrl, String candidateHref) {
        String a = RagWebCrawlUrlSupport.normalizeUrl(currentListUrl);
        String b = RagWebCrawlUrlSupport.normalizeUrl(candidateHref);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        try {
            java.net.URI ua = new java.net.URI(a);
            java.net.URI ub = new java.net.URI(b);
            if (ua.getHost() == null || ub.getHost() == null || !ua.getHost().equalsIgnoreCase(ub.getHost())) {
                return false;
            }
            String pathA = stripPageSuffix(ua.getPath());
            String pathB = stripPageSuffix(ub.getPath());
            if (pathA.isEmpty() || pathB.isEmpty()) {
                return false;
            }
            return pathB.startsWith(pathA) || pathA.startsWith(pathB) || sameDirectory(pathA, pathB);
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripPageSuffix(String path) {
        if (path == null) {
            return "";
        }
        return path.replaceAll("/index\\.(s?html?|aspx?|php|jsp)$", "/")
                .replaceAll("/list_?\\d+\\.(s?html?|aspx?|php|jsp)$", "/list.")
                .replaceAll("_\\d+\\.(s?html?|aspx?|php|jsp)$", ".");
    }

    private static boolean sameDirectory(String pathA, String pathB) {
        int la = pathA.lastIndexOf('/');
        int lb = pathB.lastIndexOf('/');
        if (la <= 0 || lb <= 0) {
            return false;
        }
        String dirA = pathA.substring(0, la);
        String dirB = pathB.substring(0, lb);
        return dirA.equals(dirB);
    }
}
