package com.aaron.cloud.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** 解析客户端 IP（优先 {@code X-Forwarded-For} 首段）。 */
public final class HttpClientIp {

    private HttpClientIp() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String x = request.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) {
            return x.split(",")[0].trim();
        }
        String addr = request.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }
}
