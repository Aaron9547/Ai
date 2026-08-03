package com.aaron.cloud.rag;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析 HTML 内 meta refresh 跳转（北方网等文章页仅返回跳转壳）。 */
public final class RagHtmlRedirectSupport {

    private static final Pattern META_REFRESH_URL =
            Pattern.compile(
                    "<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]+content\\s*=\\s*[\"'][^\"']*url\\s*=\\s*([^\"'>\\s;]+)",
                    Pattern.CASE_INSENSITIVE);

    private RagHtmlRedirectSupport() {}

    /**
     * @param html 已按正确 charset 解码的 HTML
     * @param baseUrl 当前页 URL，用于相对路径
     * @return 跳转目标，无则 empty
     */
    public static String extractMetaRefreshTarget(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Matcher m = META_REFRESH_URL.matcher(html);
        if (!m.find()) {
            return "";
        }
        String target = m.group(1).trim().replace("&amp;", "&");
        if (target.isBlank()) {
            return "";
        }
        try {
            if (target.startsWith("http://") || target.startsWith("https://")) {
                return target;
            }
            return URI.create(baseUrl).resolve(target).toString();
        } catch (Exception e) {
            return target;
        }
    }

    /** 仅含脚本 + meta refresh、几乎无正文的壳页。 */
    public static boolean isRedirectShell(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String t = html.replaceAll("\\s+", "");
        if (t.length() > 2500) {
            return false;
        }
        return META_REFRESH_URL.matcher(html).find()
                && !html.toLowerCase().contains("<p")
                && !html.toLowerCase().contains("id=\"content")
                && !html.toLowerCase().contains("class=\"content");
    }
}
