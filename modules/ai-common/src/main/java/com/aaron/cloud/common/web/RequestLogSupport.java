package com.aaron.cloud.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从 {@link RequestContextHolder} 取当前 HTTP 摘要，供异常日志排障（不含 body）。 */
public final class RequestLogSupport {

    private RequestLogSupport() {}

    /**
     * @return 形如 {@code POST /api/v1/admin/users}；无请求上下文时返回空串；query 过长时截断
     */
    public static String currentRequestLine() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes sra)) {
                return "";
            }
            HttpServletRequest r = sra.getRequest();
            String uri = r.getRequestURI();
            String q = r.getQueryString();
            if (q != null && !q.isEmpty()) {
                if (q.length() > 160) {
                    q = q.substring(0, 160) + "…";
                }
                uri = uri + "?" + q;
            }
            return r.getMethod() + " " + uri;
        } catch (Exception ignored) {
            return "";
        }
    }
}
