package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.gateway.AccessPartyHttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AccessPartyFilterSkipPolicy {

    public static boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        if (uri.startsWith("/actuator/")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")) {
            return true;
        }
        String method = request.getMethod();
        if (method != null && HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        if (uri.startsWith("/api/")) {
            return true;
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken && auth.isAuthenticated()) {
            return true;
        }
        if (uri.startsWith("/open/") && !hasAccessPartySignatureHeaders(request)) {
            return true;
        }
        return false;
    }

    public static boolean requiresAccessPartyChain(HttpServletRequest request) {
        if (shouldSkip(request)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/partner/v1/")) {
            return true;
        }
        return uri != null && uri.startsWith("/open/") && hasAccessPartySignatureHeaders(request);
    }

    private static boolean hasAccessPartySignatureHeaders(HttpServletRequest request) {
        return headerPresent(request, AccessPartyHttpHeaders.APP_ID)
                && headerPresent(request, AccessPartyHttpHeaders.SIGNATURE);
    }

    private static boolean headerPresent(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v != null && !v.isBlank();
    }
}
