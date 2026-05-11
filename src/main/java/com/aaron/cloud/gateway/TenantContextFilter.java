package com.aaron.cloud.gateway;

import com.aaron.cloud.common.api.enums.TenantMemberRole;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.security.TenantJwtTmsParser;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 须在 Spring Security 之后执行（见 {@link TenantFilterConfiguration} 注册顺序），以便从 JWT 覆盖/补充租户上下文。
 *
 * <p>创始人（JWT {@code tmr=FOUNDER}）可通过 {@code X-Tenant-Id} 或 {@code X-Tenant-Code} 切换<strong>数据租户</strong>（校验租户存在）。
 *
 * <p>非创始人：请求头中的目标租户（{@code X-Tenant-Id} 数字，或 {@code X-Tenant-Code} 解析为 id）仅当出现在 JWT {@code tms} 且规则允许时生效；否则返回 403，防止伪造头跨租户。
 */
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final SysTenantRepository tenantRepository;
    private final long defaultTenantId;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Long tenantId = null;
            Long userId = null;
            String deviceId = null;
            Long jwtTenantId = null;
            TenantMemberRole memberRole = null;

            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                jwtTenantId = toLong(jwt.getClaim("tid"));
                tenantId = jwtTenantId;
                userId = toLong(jwt.getClaim("uid"));
                deviceId = jwt.getClaimAsString("did");
                memberRole = TenantMemberRole.fromClaim(jwt.getClaimAsString("tmr"));
                String tmsJson = jwt.getClaimAsString("tms");
                List<TenantJwtTmsParser.Entry> tmsEntries = TenantJwtTmsParser.parse(tmsJson);

                if (memberRole != null && memberRole.isFounder()) {
                    Long headerTenant = resolveTenantHeaderAsNumericTenantId(request);
                    if (headerTenant != null) {
                        if (tenantRepository.findById(headerTenant).isEmpty()) {
                            response.sendError(HttpServletResponse.SC_NOT_FOUND, "tenant not found");
                            return;
                        }
                        tenantId = headerTenant;
                    } else {
                        tenantId = jwtTenantId;
                    }
                } else if (!tmsEntries.isEmpty()) {
                    Long headerTenant = resolveTenantHeaderAsNumericTenantId(request);
                    if (headerTenant != null) {
                        TenantJwtTmsParser.Entry match =
                                tmsEntries.stream()
                                        .filter(e -> e.tenantId() == headerTenant)
                                        .findFirst()
                                        .orElse(null);
                        if (match == null) {
                            response.sendError(
                                    HttpServletResponse.SC_FORBIDDEN, "not a member of target tenant");
                            return;
                        }
                        if (match.role() == TenantMemberRole.MEMBER) {
                            String uri = request.getRequestURI();
                            if (uri != null && uri.startsWith("/api/v1/admin/")) {
                                response.sendError(
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "member cannot select tenant for admin context");
                                return;
                            }
                            tenantId = headerTenant;
                            memberRole = TenantMemberRole.MEMBER;
                        } else {
                            tenantId = headerTenant;
                            memberRole = match.role();
                        }
                    } else if (jwtTenantId != null) {
                        final long tidForTmsMatch = jwtTenantId.longValue();
                        TenantMemberRole fromTms =
                                tmsEntries.stream()
                                        .filter(e -> e.tenantId() == tidForTmsMatch)
                                        .map(TenantJwtTmsParser.Entry::role)
                                        .findFirst()
                                        .orElse(null);
                        if (fromTms != null) {
                            memberRole = fromTms;
                        }
                    }
                }
            }

            if (tenantId == null) {
                tenantId = resolveTenantIdFromHeaders(request);
            }
            if (userId == null) {
                userId = parseLongHeader(request, "X-User-Id");
            }
            if (deviceId == null) {
                deviceId = headerOrNull(request, "X-Device-Id");
            }

            String traceId = headerOrNull(request, "X-Request-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = java.util.UUID.randomUUID().toString();
            }
            MDC.put("traceId", traceId);
            MDC.put("tenantId", tenantId != null ? tenantId.toString() : "");
            if (deviceId != null) {
                MDC.put("deviceId", deviceId);
            }
            TenantContextHolder.set(
                    TenantContextHolder.TenantSnapshot.builder()
                            .tenantId(tenantId)
                            .userId(userId)
                            .deviceId(deviceId)
                            .memberRole(memberRole)
                            .build());
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("traceId");
            MDC.remove("tenantId");
            MDC.remove("deviceId");
        }
    }

    private Long resolveTenantIdFromHeaders(HttpServletRequest request) {
        String tid = headerOrNull(request, "X-Tenant-Id");
        if (tid != null && !tid.isBlank()) {
            return Long.parseLong(tid.trim());
        }
        String code = headerOrNull(request, "X-Tenant-Code");
        if (code != null && !code.isBlank()) {
            return tenantRepository
                    .findByCode(code.trim())
                    .map(t -> t.getId())
                    .orElse(defaultTenantId);
        }
        return defaultTenantId;
    }

    /**
     * 与 C 端路径「第一段为租户编码」对齐：优先 {@code X-Tenant-Id}，否则将 {@code X-Tenant-Code} 解析为
     * {@code sys_tenant.id}；未知编码返回 {@code null}（由调用方决定是否沿用 JWT 默认租户）。
     */
    private Long resolveTenantHeaderAsNumericTenantId(HttpServletRequest request) {
        Long tid = parseLongHeaderNullable(request, "X-Tenant-Id");
        if (tid != null) {
            return tid;
        }
        String code = headerOrNull(request, "X-Tenant-Code");
        if (code == null || code.isBlank()) {
            return null;
        }
        return tenantRepository.findByCode(code.trim()).map(t -> t.getId()).orElse(null);
    }

    private static Long parseLongHeader(HttpServletRequest request, String name) {
        String v = headerOrNull(request, name);
        if (v == null || v.isBlank()) {
            return null;
        }
        return Long.parseLong(v.trim());
    }

    private static Long parseLongHeaderNullable(HttpServletRequest request, String name) {
        try {
            return parseLongHeader(request, name);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String headerOrNull(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v == null || v.isBlank() ? null : v;
    }

    private static Long toLong(Object claim) {
        if (claim == null) {
            return null;
        }
        if (claim instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(claim.toString());
    }
}
