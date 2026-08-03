package com.aaron.cloud.rag.crawl.fetch;

import com.aaron.cloud.rag.RagHtmlCharsetDetector;
import com.aaron.cloud.rag.RagHtmlRedirectSupport;
import com.aaron.cloud.rag.RagWebCrawlConstants;
import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 共享连接池的 HTTP 拉取；支持条件请求与 meta refresh。 */
@Slf4j
@Component
public class HttpFetcher {

    private volatile HttpClient sharedClient =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    public record FetchResult(
            int statusCode,
            String contentType,
            Charset charset,
            byte[] body,
            String finalUrl,
            String etag,
            String lastModified) {}

    public FetchResult fetch(
            String url,
            EffectiveSiteCrawlPolicy.FetchPolicy policy,
            String referer,
            String etag,
            String lastModified)
            throws Exception {
        if (policy.conditionalRequest() && etag != null && !etag.isBlank()) {
            FetchResult conditional = fetchOnce(url, policy, referer, etag, lastModified);
            if (conditional.statusCode() == 304) {
                return conditional;
            }
        }
        int hops = Math.max(0, policy.metaRefreshMaxHops());
        String current = url;
        String refererHeader = referer;
        FetchResult last = null;
        for (int hop = 0; hop <= hops; hop++) {
            last = fetchOnce(current, policy, refererHeader, null, null);
            if (last.statusCode() == 304 || last.body() == null || last.body().length == 0) {
                return last;
            }
            String html = new String(last.body(), last.charset());
            if (!RagHtmlRedirectSupport.isRedirectShell(html) || hop >= hops) {
                return last;
            }
            String next = RagHtmlRedirectSupport.extractMetaRefreshTarget(html, current);
            if (next.isBlank() || next.equals(current)) {
                return last;
            }
            log.info("meta refresh {} -> {}", current, next);
            refererHeader = current;
            current = next;
        }
        return last;
    }

    private FetchResult fetchOnce(
            String url,
            EffectiveSiteCrawlPolicy.FetchPolicy policy,
            String referer,
            String etag,
            String lastModified)
            throws Exception {
        HttpClient client = policy.sharedHttpClient() ? sharedClient : sharedClient;
        int limit = Math.min(Math.max(policy.maxBodyBytes(), 4096), 5_242_880);
        HttpRequest.Builder rb =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(Math.max(5000, policy.timeoutMs())))
                        .header("User-Agent", RagWebCrawlConstants.USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .GET();
        if (referer != null && !referer.isBlank()) {
            rb.header("Referer", referer);
        }
        if (etag != null && !etag.isBlank()) {
            rb.header("If-None-Match", etag);
        }
        if (lastModified != null && !lastModified.isBlank()) {
            rb.header("If-Modified-Since", lastModified);
        }
        HttpResponse<InputStream> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        String ct = resp.headers().firstValue("Content-Type").orElse("");
        String respEtag = resp.headers().firstValue("ETag").orElse(null);
        String respLm = resp.headers().firstValue("Last-Modified").orElse(null);
        String finalUrl = resp.uri() != null ? resp.uri().toString() : url;
        if (code == 304) {
            return new FetchResult(304, ct, Charset.forName("UTF-8"), new byte[0], finalUrl, respEtag, respLm);
        }
        if (code < 200 || code >= 300) {
            throw new HttpFetcherException(code, "http status " + code + " url=" + url);
        }
        try (InputStream in = resp.body();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > limit) {
                    throw new IllegalStateException("response too large");
                }
                bos.write(buf, 0, n);
            }
            Charset cs = RagHtmlCharsetDetector.resolve(ct, bos.toByteArray());
            return new FetchResult(code, ct, cs, bos.toByteArray(), finalUrl, respEtag, respLm);
        }
    }
}
