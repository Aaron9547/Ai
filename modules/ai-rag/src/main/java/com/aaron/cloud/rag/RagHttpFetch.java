package com.aaron.cloud.rag;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 受控 HTTP 拉取：charset 嗅探、meta refresh 一次跟随。
 *
 * @deprecated 站点爬取与预览请使用 {@link com.aaron.cloud.rag.crawl.fetch.HttpFetcher} +
 *     {@link com.aaron.cloud.rag.crawl.fetch.PolitenessGate}。
 */
@Deprecated(since = "0.1.245")
public final class RagHttpFetch {

    private static final Logger log = LoggerFactory.getLogger(RagHttpFetch.class);
    private static final int MAX_BYTES = 5_000_000;
    private static final int MAX_REDIRECT_HOPS = 2;

    private RagHttpFetch() {}

    public record Fetched(String contentType, Charset charset, byte[] body, String finalUrl) {}

    public static Fetched get(String url) throws Exception {
        return get(url, MAX_BYTES);
    }

    public static Fetched get(String url, int maxBytes) throws Exception {
        return get(url, maxBytes, null);
    }

    public static Fetched get(String url, int maxBytes, String referer) throws Exception {
        int limit = maxBytes > 0 ? Math.min(maxBytes, MAX_BYTES) : MAX_BYTES;
        String current = url;
        String refererHeader = referer;
        Fetched last = null;
        for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
            last = fetchOnce(current, limit, refererHeader);
            Charset cs = last.charset();
            String html = new String(last.body(), cs);
            if (!RagHtmlRedirectSupport.isRedirectShell(html) || hop >= MAX_REDIRECT_HOPS) {
                return last;
            }
            String next = RagHtmlRedirectSupport.extractMetaRefreshTarget(html, current);
            if (next.isBlank() || next.equals(current)) {
                return last;
            }
            log.info("跟随 meta refresh url={} -> {}", current, next);
            refererHeader = current;
            current = next;
        }
        return last;
    }

    private static Fetched fetchOnce(String url, int limit, String referer) throws Exception {
        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
        HttpRequest.Builder rb =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(RagWebCrawlConstants.FETCH_TIMEOUT_MS))
                        .header("User-Agent", RagWebCrawlConstants.USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .GET();
        if (referer != null && !referer.isBlank()) {
            rb.header("Referer", referer);
        }
        HttpResponse<InputStream> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("http status " + code);
        }
        String ct = resp.headers().firstValue("Content-Type").orElse("");
        try (InputStream in = resp.body();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > limit) {
                    throw new IllegalStateException("response too large");
                }
                bos.write(buf, 0, n);
            }
            byte[] body = bos.toByteArray();
            Charset cs = RagHtmlCharsetDetector.resolve(ct, body);
            return new Fetched(ct, cs, body, url);
        }
    }

    /** 将响应体按嗅探 charset 解码为 HTML 字符串。 */
    public static String decodeHtml(Fetched fetched) {
        return new String(fetched.body(), fetched.charset());
    }
}
