package com.aaron.cloud.chat.websearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 内置固定联网源出站 HTTP。
 *
 * <p>默认直连（国内部署）。本地开梯子访问 Google/DDG/维基时，任选其一：
 *
 * <ul>
 *   <li>租户 Shell：{@code WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON}（管理端「固定源 HTTP 代理」）
 *   <li>{@code application-local.yml}：{@code ai.websearch.fixed.proxy-host}、{@code proxy-port: 7890}
 *   <li>启动参数：{@code -Dai.websearch.fixed.proxyHost=127.0.0.1 -Dai.websearch.fixed.proxyPort=7890}
 *   <li>环境变量：{@code AI_WEB_SEARCH_FIXED_PROXY_HOST}、{@code AI_WEB_SEARCH_FIXED_PROXY_PORT}
 *   <li>本地梯子快捷：{@code use-system-proxy: true}（映射为 {@code 127.0.0.1:7890 HTTP}，勿依赖 JVM SOCKS）
 *   <li>IDEA 已配 {@code -Dhttps.proxyHost} 时自动走 JVM 代理属性（无需再配）
 * </ul>
 */
public final class WebSearchFixedSourceHttp {

    /** 连接 + 读超时（毫秒）。 */
    public static final int TIMEOUT_MS = 10_000;

    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static volatile WebSearchFixedSourceOutboundConfig globalConfig;

    private WebSearchFixedSourceHttp() {}

    /** 由 {@link WebSearchFixedSourceHttpProperties} 在 Spring 启动后注入（进程级回退）。 */
    static synchronized void applySpringSettings(
            String proxyHost, Integer proxyPort, String proxyType, Boolean useSystemProxy) {
        globalConfig =
                WebSearchFixedSourceOutboundConfig.resolve(
                        proxyHost, proxyPort, proxyType, useSystemProxy);
    }

    static WebSearchFixedSourceOutboundConfig globalConfig() {
        ensureGlobalInitialized();
        return globalConfig;
    }

    public static String outboundDiagnostics() {
        return WebSearchFixedSourceOutboundConfig.diagnostics(globalConfig());
    }

    static byte[] fetchBytes(WebSearchFixedSourceOutboundConfig config, String urlString) throws IOException {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = openHttpConnection(config, url);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                throw new IOException("HTTP " + code + "，无响应体：" + urlString);
            }
            return readAll(stream);
        } finally {
            connection.disconnect();
        }
    }

    private static void ensureGlobalInitialized() {
        if (globalConfig != null) {
            return;
        }
        synchronized (WebSearchFixedSourceHttp.class) {
            if (globalConfig == null) {
                applySpringSettings(null, null, null, null);
            }
        }
    }

    private static HttpURLConnection openHttpConnection(WebSearchFixedSourceOutboundConfig config, URL url)
            throws IOException {
        HttpURLConnection connection;
        if (config.mode() == WebSearchFixedSourceOutboundConfig.Mode.SYSTEM) {
            connection = (HttpURLConnection) url.openConnection();
        } else {
            connection = (HttpURLConnection) url.openConnection(config.proxy());
        }
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        return connection;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            if (n > 0) {
                buf.write(chunk, 0, n);
            }
        }
        return buf.toByteArray();
    }

    static RestClient buildRestClient(WebSearchFixedSourceOutboundConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(TIMEOUT_MS));
        factory.setReadTimeout(Duration.ofMillis(TIMEOUT_MS));
        if (config.mode() == WebSearchFixedSourceOutboundConfig.Mode.EXPLICIT
                || config.mode() == WebSearchFixedSourceOutboundConfig.Mode.JVM_PROPERTIES) {
            factory.setProxy(config.proxy());
        } else if (config.mode() == WebSearchFixedSourceOutboundConfig.Mode.SYSTEM) {
            // 依赖 java.net.useSystemProxies，不强制 NO_PROXY
        } else {
            factory = directRequestFactory();
        }
        return RestClient.builder().requestFactory(factory).build();
    }

    private static SimpleClientHttpRequestFactory directRequestFactory() {
        return new SimpleClientHttpRequestFactory() {
            @Override
            protected HttpURLConnection openConnection(URL url, Proxy proxy) throws IOException {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                return connection;
            }
        };
    }
}
