package com.aaron.cloud.chat.websearch;

import com.aaron.cloud.common.tenant.runtime.TenantRuntimeSettingApplicationService;
import com.aaron.cloud.common.tenant.runtime.WebSearchFixedSourceOutboundRuntime;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 按租户解析出站代理的固定源 HTTP（优先 Shell 配置，否则进程级 {@link WebSearchFixedSourceHttp}）。 */
@Service
@RequiredArgsConstructor
public class WebSearchFixedSourceHttpService {

    private final TenantRuntimeSettingApplicationService tenantRuntimeSettingApplicationService;
    private final ConcurrentHashMap<String, RestClient> restClients = new ConcurrentHashMap<>();

    public Document jsoupGet(long tenantId, String url) throws IOException {
        return jsoupGet(tenantId, url, StandardCharsets.UTF_8);
    }

    public Document jsoupGet(long tenantId, String url, Charset charset) throws IOException {
        byte[] body = WebSearchFixedSourceHttp.fetchBytes(configFor(tenantId), url);
        String html = new String(body, charset);
        return org.jsoup.Jsoup.parse(html, url);
    }

    public RestClient restClient(long tenantId) {
        WebSearchFixedSourceOutboundConfig config = configFor(tenantId);
        String key = config.label();
        return restClients.computeIfAbsent(key, k -> WebSearchFixedSourceHttp.buildRestClient(config));
    }

    public String outboundDiagnostics(long tenantId) {
        return WebSearchFixedSourceOutboundConfig.diagnostics(configFor(tenantId));
    }

    private WebSearchFixedSourceOutboundConfig configFor(long tenantId) {
        WebSearchFixedSourceOutboundRuntime tenant =
                tenantRuntimeSettingApplicationService.webSearchFixedSourceOutbound(tenantId);
        return WebSearchFixedSourceOutboundConfig.resolveForTenant(
                tenant, WebSearchFixedSourceHttp.globalConfig());
    }
}
