package com.aaron.cloud.common.tenant.runtime;

/**
 * 内置固定联网源（DDG/Google/维基/百度 HTML）出站代理；存于 {@code WEB_SEARCH_FIXED_SOURCE_OUTBOUND_JSON}。
 *
 * @param enabled 为 true 且 host 非空时，固定源 HTTP 走代理
 * @param host 代理主机，如 {@code 127.0.0.1}
 * @param port 代理端口，Clash HTTP 常见 7890
 * @param type {@code HTTP} 或 {@code SOCKS}
 */
public record WebSearchFixedSourceOutboundRuntime(
        boolean enabled, String host, int port, String type) {

    public static final String TYPE_HTTP = "HTTP";
    public static final String TYPE_SOCKS = "SOCKS";

    public static WebSearchFixedSourceOutboundRuntime disabled() {
        return new WebSearchFixedSourceOutboundRuntime(false, "", 7890, TYPE_HTTP);
    }

    public String normalizedType() {
        if (type != null && TYPE_SOCKS.equalsIgnoreCase(type.trim())) {
            return TYPE_SOCKS;
        }
        return TYPE_HTTP;
    }
}
