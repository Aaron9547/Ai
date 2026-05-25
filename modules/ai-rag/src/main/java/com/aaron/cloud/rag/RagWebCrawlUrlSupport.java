package com.aaron.cloud.rag;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 本地规则网页爬取 URL 过滤（对齐 ly-ai-application LocalWebCrawlUrls 语义）。 */
public final class RagWebCrawlUrlSupport {

    public static final int MAX_URLS_TOTAL = 20_000;
    public static final int URL_MAX_CODEPOINTS = 255;
    private static final int DEFAULT_MAX_DEPTH = 3;

    private RagWebCrawlUrlSupport() {}

    public static int normalizeDepth(Integer maxDepth) {
        if (maxDepth == null || maxDepth < 1) {
            return DEFAULT_MAX_DEPTH;
        }
        return Math.min(maxDepth, 8);
    }

    public static String normalizeUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        try {
            URI u = new URI(t);
            if (u.getScheme() == null || u.getHost() == null) {
                return t;
            }
            String path = u.getPath() == null ? "" : u.getPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            String q = u.getQuery();
            String frag = u.getFragment();
            StringBuilder sb = new StringBuilder();
            sb.append(u.getScheme().toLowerCase(Locale.ROOT)).append("://").append(u.getHost().toLowerCase(Locale.ROOT));
            if (u.getPort() > 0 && u.getPort() != 80 && u.getPort() != 443) {
                sb.append(':').append(u.getPort());
            }
            sb.append(path.isEmpty() ? "" : path);
            if (q != null && !q.isBlank()) {
                sb.append('?').append(q);
            }
            if (frag != null && !frag.isBlank()) {
                sb.append('#').append(frag);
            }
            return sb.toString();
        } catch (Exception e) {
            return t;
        }
    }

    public static List<String> sanitizeForCrawl(List<String> urls, String baseUrl) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        String root = resolveRootDomain(baseUrl);
        List<String> out = new ArrayList<>(urls.size());
        for (String u : urls) {
            if (u == null) {
                continue;
            }
            String t = normalizeUrl(u);
            if (t.isEmpty() || isStaticResource(t)) {
                continue;
            }
            if (root != null && !isInRootDomain(t, root)) {
                continue;
            }
            if (t.codePointCount(0, t.length()) > URL_MAX_CODEPOINTS) {
                continue;
            }
            out.add(t);
        }
        if (out.size() > MAX_URLS_TOTAL) {
            return out.subList(0, MAX_URLS_TOTAL);
        }
        return out;
    }

    public static boolean isStaticResource(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(css|js|jpg|jpeg|png|gif|svg|ico|woff2?|ttf|eot|mp4|mp3|zip|rar|pdf|docx?|xlsx?)(\\?.*)?$");
    }

    public static String resolveRootDomain(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            return toRootDomain(host.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isInRootDomain(String rawUrl, String rootDomain) {
        if (rawUrl == null || rootDomain == null) {
            return false;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            String root = rootDomain.toLowerCase(Locale.ROOT);
            return h.equals(root) || h.endsWith("." + root);
        } catch (Exception e) {
            return false;
        }
    }

    private static String toRootDomain(String host) {
        String[] arr = host.split("\\.");
        if (arr.length <= 2) {
            return host;
        }
        boolean cnSecond = host.endsWith(".edu.cn")
                || host.endsWith(".gov.cn")
                || host.endsWith(".org.cn")
                || host.endsWith(".com.cn")
                || host.endsWith(".net.cn");
        if (cnSecond && arr.length >= 3) {
            return arr[arr.length - 3] + "." + arr[arr.length - 2] + "." + arr[arr.length - 1];
        }
        return arr[arr.length - 2] + "." + arr[arr.length - 1];
    }
}
