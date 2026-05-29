package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.outbound.WebSearchFixedSourceOutboundConfig;
import com.aaron.cloud.common.outbound.WebSearchFixedSourceOutboundResolver;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 按租户解析出站代理的固定源 HTTP（优先 Shell 配置，否则进程级 {@link WebSearchFixedSourceOutboundResolver}）。 */
@Service
@RequiredArgsConstructor
public class WebSearchFixedSourceHttpService {

    private final WebSearchFixedSourceOutboundResolver outboundResolver;
    private final ConcurrentHashMap<String, RestClient> restClients = new ConcurrentHashMap<>();

    /** 国内站点（如百度）强制直连，不受租户/进程代理影响。 */
    public Document jsoupGetDirect(String url) throws IOException {
        return jsoupGetDirect(url, StandardCharsets.UTF_8);
    }

    public Document jsoupGetDirect(String url, Charset charset) throws IOException {
        byte[] body =
                WebSearchFixedSourceHttp.fetchBytes(WebSearchFixedSourceOutboundConfig.direct(), url);
        String html = new String(body, charset);
        return org.jsoup.Jsoup.parse(html, url);
    }

    public Document jsoupGet(long tenantId, String url) throws IOException {
        return jsoupGet(tenantId, url, StandardCharsets.UTF_8);
    }

    public Document jsoupGet(long tenantId, String url, Charset charset) throws IOException {
        byte[] body = WebSearchFixedSourceHttp.fetchBytes(outboundResolver.resolve(tenantId), url);
        String html = new String(body, charset);
        return org.jsoup.Jsoup.parse(html, url);
    }

    public RestClient restClient(long tenantId) {
        WebSearchFixedSourceOutboundConfig config = outboundResolver.resolve(tenantId);
        String key = config.label();
        return restClients.computeIfAbsent(key, k -> WebSearchFixedSourceHttp.buildRestClient(config));
    }

    public String outboundDiagnostics(long tenantId) {
        return outboundResolver.diagnostics(tenantId);
    }
}
