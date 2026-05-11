package com.aaron.cloud.rag;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 鍙楁帶 HTTP 鎷夊彇锛堝ぇ灏忎笂闄愶級锛涗粎鐢ㄤ簬鍏佽鐨?http(s) URL銆?*/
public final class RagHttpFetch {

    private static final Logger log = LoggerFactory.getLogger(RagHttpFetch.class);
    private static final int MAX_BYTES = 5_000_000;

    private RagHttpFetch() {}

    public record Fetched(String contentType, Charset charset, byte[] body) {}

    public static Fetched get(String url) throws Exception {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(45))
                        .header("User-Agent", "AiKnowledgeCenter/1.0 (+https://localhost)")
                        .GET()
                        .build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("http status " + code);
        }
        String ct = resp.headers().firstValue("Content-Type").orElse("");
        Charset cs = StandardCharsets.UTF_8;
        if (ct.toLowerCase().contains("charset=")) {
            String name = ct.substring(ct.toLowerCase().indexOf("charset=") + 8).split(";")[0].trim().replace("\"", "");
            try {
                cs = Charset.forName(name);
            } catch (Exception e) {
                log.warn("unknown charset name={}", name, e);
                cs = StandardCharsets.UTF_8;
            }
        }
        try (InputStream in = resp.body();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IllegalStateException("response too large");
                }
                bos.write(buf, 0, n);
            }
            return new Fetched(ct, cs, bos.toByteArray());
        }
    }
}
