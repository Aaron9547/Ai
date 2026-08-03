package com.aaron.cloud.gateway.accessparty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccessPartyHmacSupport {

    private static final long TIMESTAMP_SKEW_SECONDS = 300L;

    public static String buildCanonicalPathWithQuery(HttpServletRequest request) {
        String path = request.getRequestURI();
        String qs = request.getQueryString();
        if (qs == null || qs.isBlank()) {
            return path;
        }
        Map<String, List<String>> params = new TreeMap<>();
        for (String part : qs.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            String val = eq >= 0 ? part.substring(eq + 1) : "";
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
        }
        StringBuilder sb = new StringBuilder(path);
        sb.append('?');
        boolean first = true;
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            List<String> vals = new ArrayList<>(e.getValue());
            Collections.sort(vals);
            for (String v : vals) {
                if (!first) {
                    sb.append('&');
                }
                first = false;
                sb.append(e.getKey()).append('=').append(v);
            }
        }
        return sb.toString();
    }

    public static String sha256Hex(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(body == null ? new byte[0] : body);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("sha256 failed", ex);
        }
    }

    public static String buildCanonicalString(
            String method, String pathWithQuery, String timestamp, String nonce, byte[] body) {
        String m = method == null ? "" : method.trim().toUpperCase();
        return m + "\n" + pathWithQuery + "\n" + timestamp + "\n" + nonce + "\n" + sha256Hex(body);
    }

    public static boolean verifyTimestamp(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestampHeader.trim());
            long now = System.currentTimeMillis() / 1000L;
            return Math.abs(now - ts) <= TIMESTAMP_SKEW_SECONDS;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static boolean verifySignature(String secret, String canonical, String signatureHeader) {
        if (secret == null || secret.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            String expected = java.util.Base64.getEncoder().encodeToString(raw);
            return constantTimeEquals(expected, signatureHeader.trim());
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
