package com.aaron.cloud.rag;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/** 从 sitemap 读取种子 URL（对齐 ly-ai-application JsoupHelper#fetchSitemapSeedUrls）。 */
public final class RagSitemapSeedSupport {

    private static final int FETCH_TIMEOUT_MS = 20_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AiRagLocalCrawler/1.0; +https://ly-ai.local)";

    @FunctionalInterface
    public interface SitemapPageLoader {
        Document load(String url) throws Exception;
    }

    private RagSitemapSeedSupport() {}

    public static List<String> fetchSeedUrls(String baseUrl, String rootDomain, int maxUrls) {
        return fetchSeedUrls(baseUrl, rootDomain, maxUrls, null);
    }

    public static List<String> fetchSeedUrls(
            String baseUrl, String rootDomain, int maxUrls, SitemapPageLoader loader) {
        List<String> out = new ArrayList<>();
        if (baseUrl == null || baseUrl.isBlank() || rootDomain == null || maxUrls <= 0) {
            return out;
        }
        String origin = originOf(baseUrl);
        if (origin == null) {
            return out;
        }
        String[] paths = {"/sitemap.xml", "/sitemap_index.xml", "/sitemap/sitemap.xml", "/sitemap/index.xml"};
        for (String path : paths) {
            if (out.size() >= maxUrls) {
                break;
            }
            String smUrl = origin + path;
            try {
                Document doc =
                        loader != null
                                ? loader.load(smUrl)
                                : Jsoup.connect(smUrl)
                                        .userAgent(USER_AGENT)
                                        .timeout(FETCH_TIMEOUT_MS)
                                        .ignoreContentType(true)
                                        .get();
                collectLoc(doc, smUrl, rootDomain, maxUrls, out, 3, loader);
            } catch (Exception ignored) {
                // 忽略单个 sitemap 失败
            }
        }
        return out;
    }

    private static void collectLoc(
            Document doc,
            String baseForRelative,
            String rootDomain,
            int maxUrls,
            List<String> out,
            int depthLeft,
            SitemapPageLoader loader) {
        if (depthLeft <= 0) {
            return;
        }
        Elements locs = doc.select("loc");
        for (Element loc : locs) {
            if (out.size() >= maxUrls) {
                return;
            }
            String locText = loc.text().trim();
            if (locText.isEmpty()) {
                continue;
            }
            String abs = locText.startsWith("http") ? locText : resolve(baseForRelative, locText);
            abs = stripFragment(abs);
            if (!RagWebCrawlUrlSupport.isInRootDomain(abs, rootDomain)) {
                continue;
            }
            String lower = abs.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".xml") && lower.contains("sitemap")) {
                try {
                    Document child =
                            loader != null
                                    ? loader.load(abs)
                                    : Jsoup.connect(abs)
                                            .userAgent(USER_AGENT)
                                            .timeout(FETCH_TIMEOUT_MS)
                                            .ignoreContentType(true)
                                            .parser(Parser.xmlParser())
                                            .get();
                    collectLoc(child, abs, rootDomain, maxUrls, out, depthLeft - 1, loader);
                } catch (Exception ignored) {
                    // skip child sitemap
                }
            } else if (!lower.endsWith(".xml")) {
                String norm = RagWebCrawlUrlSupport.normalizeUrl(abs);
                if (!norm.isEmpty() && !out.contains(norm)) {
                    out.add(norm);
                }
            }
        }
    }

    private static String originOf(String baseUrl) {
        try {
            URI u = new URI(baseUrl.trim());
            if (u.getScheme() == null || u.getHost() == null) {
                return null;
            }
            int port = u.getPort();
            return port > 0 ? u.getScheme() + "://" + u.getHost() + ":" + port : u.getScheme() + "://" + u.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolve(String base, String href) {
        try {
            return new URI(base).resolve(href).toString();
        } catch (Exception e) {
            return href;
        }
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }
}
