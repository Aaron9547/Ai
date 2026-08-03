package com.aaron.cloud.common.outbound;

import feign.FeignException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 判断异常是否属于「凭证失效 / 欠费 / 配额」等租户或密钥层面的硬错误，用于租户级短期隔离计数（避免无效重试风暴）。
 */
public final class OutboundHardFailureSignals {

    private OutboundHardFailureSignals() {}

    public static boolean isCredentialBillingOrQuota(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof LlmOutboundException lo && fromLlmOutbound(lo)) {
                return true;
            }
            if (t instanceof FeignException fe) {
                int s = fe.status();
                if (s == 401 || s == 402 || s == 403) {
                    return true;
                }
                if (s == 429 && containsQuotaHints(safeUtf8(fe))) {
                    return true;
                }
                if (s == 400 && containsInvalidKeyHints(safeUtf8(fe))) {
                    return true;
                }
            }
            if (t instanceof ResponseStatusException rse) {
                int s = rse.getStatusCode().value();
                if (s == 401 || s == 402 || s == 403) {
                    return true;
                }
            }
            if (t instanceof RestClientResponseException rcre) {
                int s = rcre.getStatusCode().value();
                if (s == 401 || s == 402 || s == 403) {
                    return true;
                }
                String body = restClientBodyUtf8(rcre);
                if (s == 429 && containsQuotaHints(body)) {
                    return true;
                }
                if (s == 400 && containsInvalidKeyHints(body)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fromLlmOutbound(LlmOutboundException lo) {
        Integer st = lo.getHttpStatus();
        if (st != null) {
            if (st == 401 || st == 402 || st == 403) {
                return true;
            }
            if (st == 429) {
                return containsQuotaHints(haystack(lo));
            }
            if (st == 400) {
                return containsInvalidKeyHints(haystack(lo));
            }
        }
        return containsInvalidKeyHints(haystack(lo)) || containsQuotaHints(haystack(lo));
    }

    private static String haystack(LlmOutboundException lo) {
        String a = lo.getMessage() == null ? "" : lo.getMessage();
        String b = lo.getBodySnippet() == null ? "" : lo.getBodySnippet();
        return (a + " " + b).toLowerCase(Locale.ROOT);
    }

    private static String safeUtf8(FeignException fe) {
        try {
            String u = fe.contentUTF8();
            return u == null ? "" : u;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean containsInvalidKeyHints(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.toLowerCase(Locale.ROOT);
        return t.contains("invalid_api_key")
                || t.contains("incorrect_api_key")
                || t.contains("invalid api key");
    }

    private static boolean containsQuotaHints(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.toLowerCase(Locale.ROOT);
        return t.contains("insufficient_quota")
                || t.contains("insufficient quota")
                || t.contains("billing_hard_limit")
                || t.contains("billing")
                || t.contains("payment_required")
                || t.contains("credit")
                || t.contains("exceeded your current quota")
                || t.contains("quota")
                || t.contains("欠费")
                || t.contains("余额");
    }

    private static String restClientBodyUtf8(RestClientResponseException rcre) {
        try {
            byte[] b = rcre.getResponseBodyAsByteArray();
            return b == null ? "" : new String(b, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }
}
