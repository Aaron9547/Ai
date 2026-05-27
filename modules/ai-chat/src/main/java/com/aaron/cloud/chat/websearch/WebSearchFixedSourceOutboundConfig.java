package com.aaron.cloud.chat.websearch;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

/** 内置固定联网源出站：直连 / 显式 HTTP·SOCKS / JVM 代理属性 / 系统代理。 */
final class WebSearchFixedSourceOutboundConfig {

    enum Mode {
        DIRECT,
        EXPLICIT,
        JVM_PROPERTIES,
        SYSTEM
    }

    private final Mode mode;
    private final Proxy proxy;
    private final String label;

    private WebSearchFixedSourceOutboundConfig(Mode mode, Proxy proxy, String label) {
        this.mode = mode;
        this.proxy = proxy;
        this.label = label;
    }

    Mode mode() {
        return mode;
    }

    /** 出站用；{@link Mode#DIRECT} 时为 {@link Proxy#NO_PROXY}。 */
    Proxy proxy() {
        return proxy;
    }

    String label() {
        return label;
    }

    /** 租户 Shell 配置优先；未启用时回退进程级 {@code globalFallback}。 */
    static WebSearchFixedSourceOutboundConfig resolveForTenant(
            com.aaron.cloud.common.tenant.runtime.WebSearchFixedSourceOutboundRuntime tenant,
            WebSearchFixedSourceOutboundConfig globalFallback) {
        if (tenant != null
                && tenant.enabled()
                && tenant.host() != null
                && !tenant.host().isBlank()) {
            return explicit(tenant.host().trim(), tenant.port(), tenant.normalizedType());
        }
        return globalFallback == null ? direct() : globalFallback;
    }

    static WebSearchFixedSourceOutboundConfig resolve(
            String springProxyHost,
            Integer springProxyPort,
            String springProxyType,
            Boolean springUseSystemProxy) {
        WebSearchFixedSourceOutboundConfig fromSpring =
                fromSpring(springProxyHost, springProxyPort, springProxyType, springUseSystemProxy);
        if (fromSpring != null) {
            return fromSpring;
        }
        WebSearchFixedSourceOutboundConfig explicit = fromExplicitProperties();
        if (explicit != null) {
            return explicit;
        }
        if (flagSystemProxy()) {
            return localClashHttpExplicit(null, null, "envUseSystemProxy");
        }
        Proxy jvm = proxyFromJvmProperties();
        if (jvm != null && jvm.type() != Proxy.Type.DIRECT) {
            if (jvm.type() == Proxy.Type.SOCKS && isLoopback(jvm)) {
                return localClashHttpExplicit(
                        null, null, "jvmSocksRemappedToHttp");
            }
            return new WebSearchFixedSourceOutboundConfig(Mode.JVM_PROPERTIES, jvm, "jvmProxy:" + jvm);
        }
        return direct();
    }

    /**
     * 「系统代理」在 Windows + Clash 下 JVM 常落到 {@link Proxy.Type#SOCKS} 导致 Connect timeout；
     * 与 Postman 一致改走本地 HTTP 代理口（默认 127.0.0.1:7890）。
     */
    private static WebSearchFixedSourceOutboundConfig localClashHttpExplicit(
            Integer port, String type, String reason) {
        int p = port == null ? 7890 : port;
        String t = type != null && type.trim().equalsIgnoreCase("SOCKS") ? "SOCKS" : "HTTP";
        if (!"SOCKS".equals(t)) {
            t = "HTTP";
        }
        WebSearchFixedSourceOutboundConfig cfg = explicit("127.0.0.1", p, t);
        return new WebSearchFixedSourceOutboundConfig(
                cfg.mode(),
                cfg.proxy(),
                cfg.label() + "(" + reason + ")");
    }

    private static boolean isLoopback(Proxy proxy) {
        if (proxy == null || !(proxy.address() instanceof InetSocketAddress addr)) {
            return false;
        }
        String h = addr.getHostString();
        return "127.0.0.1".equals(h) || "localhost".equalsIgnoreCase(h) || "::1".equals(h);
    }

    private static WebSearchFixedSourceOutboundConfig fromSpring(
            String host, Integer port, String type, Boolean useSystemProxy) {
        if (host != null && !host.isBlank()) {
            return explicit(host.trim(), port == null ? 7890 : port, type);
        }
        if (Boolean.TRUE.equals(useSystemProxy)) {
            return localClashHttpExplicit(port, type, "springUseSystemProxy");
        }
        return null;
    }

    private static WebSearchFixedSourceOutboundConfig fromExplicitProperties() {
        String raw =
                firstNonBlank(
                        System.getProperty("ai.websearch.fixed.proxyHost"),
                        System.getenv("AI_WEB_SEARCH_FIXED_PROXY_HOST"),
                        System.getenv("HTTPS_PROXY"),
                        System.getenv("https_proxy"));
        if (raw == null) {
            return null;
        }
        String host;
        int port;
        if (raw.contains("://")) {
            try {
                java.net.URI uri = java.net.URI.create(raw.trim());
                host = uri.getHost();
                port = uri.getPort() > 0 ? uri.getPort() : 7890;
            } catch (RuntimeException e) {
                host = stripProxyScheme(raw);
                port = 7897;
            }
        } else {
            host = stripProxyScheme(raw);
            port =
                    parsePort(
                            firstNonBlank(
                                    System.getProperty("ai.websearch.fixed.proxyPort"),
                                    System.getenv("AI_WEB_SEARCH_FIXED_PROXY_PORT")),
                            7897);
        }
        if (host == null || host.isBlank()) {
            return null;
        }
        String type =
                firstNonBlank(
                        System.getProperty("ai.websearch.fixed.proxyType"),
                        System.getenv("AI_WEB_SEARCH_FIXED_PROXY_TYPE"));
        return explicit(host, port, type);
    }

    private static WebSearchFixedSourceOutboundConfig explicit(String host, int port, String type) {
        Proxy.Type proxyType =
                type != null && type.trim().equalsIgnoreCase("SOCKS")
                        ? Proxy.Type.SOCKS
                        : Proxy.Type.HTTP;
        Proxy proxy = new Proxy(proxyType, new InetSocketAddress(host, port));
        return new WebSearchFixedSourceOutboundConfig(
                Mode.EXPLICIT, proxy, "explicit:" + host + ":" + port + "(" + proxyType + ")");
    }

    private static boolean flagSystemProxy() {
        return boolProp("ai.websearch.fixed.useSystemProxy")
                || boolEnv("AI_WEB_SEARCH_FIXED_USE_SYSTEM_PROXY");
    }

    private static Proxy proxyFromJvmProperties() {
        String httpsHost = System.getProperty("https.proxyHost");
        String httpsPort = System.getProperty("https.proxyPort");
        if (httpsHost != null && !httpsHost.isBlank()) {
            int port = parsePort(httpsPort, 443);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpsHost.trim(), port));
        }
        String httpHost = System.getProperty("http.proxyHost");
        String httpPort = System.getProperty("http.proxyPort");
        if (httpHost != null && !httpHost.isBlank()) {
            int port = parsePort(httpPort, 80);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpHost.trim(), port));
        }
        String socksHost = System.getProperty("socksProxyHost");
        String socksPort = System.getProperty("socksProxyPort");
        if (socksHost != null && !socksHost.isBlank()) {
            int port = parsePort(socksPort, 1080);
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost.trim(), port));
        }
        return null;
    }

    private static WebSearchFixedSourceOutboundConfig direct() {
        return new WebSearchFixedSourceOutboundConfig(Mode.DIRECT, Proxy.NO_PROXY, "NO_PROXY");
    }

    static String diagnostics(WebSearchFixedSourceOutboundConfig config) {
        List<String> parts = new ArrayList<>();
        parts.add("fixedSourceMode=" + config.label());
        parts.add("timeoutMs=" + WebSearchFixedSourceHttp.TIMEOUT_MS);
        appendJvmProp(parts, "http.proxyHost");
        appendJvmProp(parts, "http.proxyPort");
        appendJvmProp(parts, "https.proxyHost");
        appendJvmProp(parts, "https.proxyPort");
        appendJvmProp(parts, "socksProxyHost");
        appendJvmProp(parts, "socksProxyPort");
        appendJvmProp(parts, "java.net.useSystemProxies");
        String toolOpts = System.getenv("JAVA_TOOL_OPTIONS");
        if (toolOpts != null && !toolOpts.isBlank()) {
            parts.add("JAVA_TOOL_OPTIONS=" + toolOpts.trim());
        }
        if (config.mode() == Mode.DIRECT) {
            parts.add(
                    "hint=境外固定源需代理时可配置 ai.websearch.fixed.proxy-host=127.0.0.1 proxy-port=7890（Clash 常见）");
        }
        return String.join("; ", parts);
    }

    private static void appendJvmProp(List<String> parts, String key) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank()) {
            parts.add(key + "=" + v.trim());
        }
    }

    private static String stripProxyScheme(String host) {
        String h = host.trim();
        if (h.startsWith("http://")) {
            return h.substring(7);
        }
        if (h.startsWith("https://")) {
            return h.substring(8);
        }
        int at = h.lastIndexOf(':');
        if (at > 0 && h.indexOf('/') < 0) {
            try {
                Integer.parseInt(h.substring(at + 1));
                return h.substring(0, at);
            } catch (NumberFormatException ignored) {
                return h;
            }
        }
        return h;
    }

    private static int parsePort(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static boolean boolProp(String key) {
        String v = System.getProperty(key);
        return v != null && Boolean.parseBoolean(v.trim());
    }

    private static boolean boolEnv(String key) {
        String v = System.getenv(key);
        return v != null && Boolean.parseBoolean(v.trim());
    }
}
