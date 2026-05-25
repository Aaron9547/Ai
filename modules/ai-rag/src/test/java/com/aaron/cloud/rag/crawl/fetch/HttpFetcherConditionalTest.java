package com.aaron.cloud.rag.crawl.fetch;

import com.aaron.cloud.rag.crawl.policy.EffectiveSiteCrawlPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpFetcherConditionalTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returns304WhenEtagMatches() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        byte[] body = "<html>ok</html>".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(
                "/page",
                exchange -> {
                    int n = hits.incrementAndGet();
                    if (n == 1) {
                        exchange.getResponseHeaders().add("ETag", "\"v1\"");
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                        exchange.sendResponseHeaders(200, body.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(body);
                        }
                    } else {
                        exchange.sendResponseHeaders(304, -1);
                        exchange.close();
                    }
                });
        server.start();
        int port = server.getAddress().getPort();
        String url = "http://127.0.0.1:" + port + "/page";

        HttpFetcher fetcher = new HttpFetcher();
        EffectiveSiteCrawlPolicy.FetchPolicy policy =
                new EffectiveSiteCrawlPolicy.FetchPolicy(
                        1_000_000, 15_000, 0, true, true);

        var first = fetcher.fetch(url, policy, null, null, null);
        assertEquals(200, first.statusCode());
        assertTrue(first.etag() != null && !first.etag().isBlank());

        var second = fetcher.fetch(url, policy, null, first.etag(), null);
        assertEquals(304, second.statusCode());
        assertEquals(2, hits.get());
    }

    @Test
    void httpFetcherExceptionRetryableFor429() {
        var ex = new HttpFetcherException(429, "rate limited");
        assertTrue(ex.retryable());
    }
}
