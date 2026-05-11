package com.aaron.cloud.gateway;

import com.aaron.cloud.common.accesslog.SysHttpAccessLogRepository;
import com.aaron.cloud.common.accesslog.entity.SysHttpAccessLog;
import com.aaron.cloud.common.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 须在 {@link TenantContextFilter} 之后注册（见 {@link TenantFilterConfiguration}），以便 finally 落库时仍能读取
 * {@link TenantContextHolder}。
 */
@Slf4j
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private final SysHttpAccessLogRepository accessLogRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            var snap = TenantContextHolder.getOrNull();
            var row = new SysHttpAccessLog();
            if (snap != null) {
                row.setTenantId(snap.getTenantId());
                row.setUserId(snap.getUserId());
                row.setDeviceId(snap.getDeviceId());
            }
            row.setMethod(request.getMethod());
            row.setPathPattern(request.getRequestURI());
            row.setHttpStatus(response.getStatus());
            row.setDurationMs(durationMs);
            row.setTraceId(request.getHeader("X-Request-Id"));
            String ua = request.getHeader("User-Agent");
            row.setUserAgent(ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua);
            row.setClientIp(request.getRemoteAddr());
            row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            try {
                accessLogRepository.insert(row);
            } catch (Exception e) {
                log.warn(
                        "access log insert failed method={} path={} status={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        e);
            }
        }
    }
}
