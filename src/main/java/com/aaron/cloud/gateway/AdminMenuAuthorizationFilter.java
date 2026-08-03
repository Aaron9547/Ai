package com.aaron.cloud.gateway;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.identity.admin.AdminMenuAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public final class AdminMenuAuthorizationFilter extends OncePerRequestFilter {

    private final AdminMenuAuthorizationService adminMenuAuthorizationService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("/api/v1/admin/me".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (TenantContextHolder.getOrNull() == null || TenantContextHolder.getOrNull().getTenantId() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!adminMenuAuthorizationService.isHttpPathAllowed(uri)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "menu not allowed for current role");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
