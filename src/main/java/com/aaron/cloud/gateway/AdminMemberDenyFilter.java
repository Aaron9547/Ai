package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.tenant.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 禁止纯 {@link TenantMemberRole#MEMBER} 访问管理端 API；放行 {@code GET /api/v1/admin/me} 供前端展示无权限原因。
 */
public final class AdminMemberDenyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/api/v1/admin/me".equals(uri)) {
            filterChain.doFilter(request, response);
            return;
        }
        var snap = TenantContextHolder.getOrNull();
        if (snap != null && snap.getMemberRole() == TenantMemberRole.MEMBER) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "member has no admin console access");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
