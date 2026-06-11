package com.aaron.cloud.common.outbound;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 出站 HTTP(S) URL 安全校验：仅允许公网 http/https，阻断 SSRF（内网、回环、链路本地、云元数据等）。
 */
public final class SafeOutboundUrlGuard {

    private SafeOutboundUrlGuard() {}

    public static URI requireHttpOrHttps(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("url required");
        }
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("unsupported url scheme");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("invalid url host");
        }
        rejectPrivateHostLiteral(host);
        try {
            InetAddress[] resolved = InetAddress.getAllByName(host);
            for (InetAddress addr : resolved) {
                if (isBlockedAddress(addr)) {
                    throw new IllegalArgumentException("url targets private network");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("unknown host", ex);
        }
        return uri;
    }

    private static void rejectPrivateHostLiteral(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lower) || lower.endsWith(".localhost")) {
            throw new IllegalArgumentException("localhost not allowed");
        }
        if (lower.equals("metadata.google.internal") || lower.endsWith(".internal")) {
            throw new IllegalArgumentException("internal host not allowed");
        }
        if (lower.startsWith("[") && lower.endsWith("]")) {
            lower = lower.substring(1, lower.length() - 1);
        }
        if (isPrivateOrLocalLiteral(lower)) {
            throw new IllegalArgumentException("private host not allowed");
        }
    }

    static boolean isPrivateOrLocalLiteral(String ipOrHost) {
        if ("unknown".equalsIgnoreCase(ipOrHost) || "localhost".equalsIgnoreCase(ipOrHost)) {
            return true;
        }
        String ip = ipOrHost;
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }
        if (ip.contains(":")) {
            String lower = ip.toLowerCase(Locale.ROOT);
            return lower.equals("::1")
                    || lower.startsWith("fe80:")
                    || lower.startsWith("fc")
                    || lower.startsWith("fd");
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(octets[0]);
            int b = Integer.parseInt(octets[1]);
            if (a == 10 || a == 127) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            if (a == 169 && b == 254) {
                return true;
            }
            return a == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int a = raw[0] & 0xff;
            int b = raw[1] & 0xff;
            if (a == 169 && b == 254) {
                return true;
            }
            if (a == 0) {
                return true;
            }
        }
        return false;
    }
}
