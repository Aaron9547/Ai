package com.aaron.cloud.common.outbound;

import lombok.Getter;

/** 统一出站失败信息，供日志、SSE error 帧与指标使用。 */
@Getter
public class LlmOutboundException extends Exception {

    private final OutboundKind kind;
    private final String code;
    private final Integer httpStatus;
    private final Long retryAfterMs;
    private final String bodySnippet;
    private final String modelAlias;

    public LlmOutboundException(
            OutboundKind kind,
            String code,
            String message,
            Integer httpStatus,
            Long retryAfterMs,
            String bodySnippet,
            String modelAlias,
            Throwable cause) {
        super(message == null ? code : message, cause, false, true);
        this.kind = kind;
        this.code = code == null ? "UNKNOWN" : code;
        this.httpStatus = httpStatus;
        this.retryAfterMs = retryAfterMs;
        this.bodySnippet = bodySnippet;
        this.modelAlias = modelAlias;
    }

    public static LlmOutboundException upstreamHttp(
            OutboundKind kind,
            int status,
            String message,
            String bodySnippet,
            String modelAlias,
            Long retryAfterMs) {
        return new LlmOutboundException(
                kind,
                "HTTP_" + status,
                message,
                status,
                retryAfterMs,
                truncate(bodySnippet, 480),
                modelAlias,
                null);
    }

    public static LlmOutboundException wrap(
            OutboundKind kind, String code, String message, String modelAlias, Throwable cause) {
        Integer st = null;
        Long ra = null;
        if (cause instanceof LlmOutboundException lo) {
            st = lo.getHttpStatus();
            ra = lo.getRetryAfterMs();
        }
        return new LlmOutboundException(kind, code, message, st, ra, null, modelAlias, cause);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
