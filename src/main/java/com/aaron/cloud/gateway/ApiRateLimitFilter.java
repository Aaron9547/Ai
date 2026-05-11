package com.aaron.cloud.gateway;

import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.gateway.GwApiRateLimitRuleRepository;
import com.aaron.cloud.common.gateway.entity.GwApiRateLimitRule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 按库表 {@code gw_api_rate_limit_rule} 对 HTTP 请求做粗粒度固定窗口限流（每分钟）。
 */
@RequiredArgsConstructor
public final class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final GwApiRateLimitRuleRepository ruleRepository;

    private volatile List<GwApiRateLimitRule> cachedRules = List.of();
    private volatile long cacheLoadedAtMillis;

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null
                || uri.startsWith("/actuator/")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")) {
            chain.doFilter(request, response);
            return;
        }
        refreshCacheIfNeeded();
        GwApiRateLimitRule hit = resolveRule(request);
        if (hit == null) {
            chain.doFilter(request, response);
            return;
        }
        long window = System.currentTimeMillis() / 60_000L;
        String rateKey = buildRateKey(request, hit, window);
        int cap = Math.max(1, hit.getRequestsPerMinute());
        AtomicInteger c = counters.computeIfAbsent(rateKey, k -> new AtomicInteger(0));
        int n = c.incrementAndGet();
        if (n > cap) {
            log.warn("rate limit exceeded key={} cap={} uri={}", rateKey, cap, uri);
            response.sendError(429, "too many requests");
            return;
        }
        chain.doFilter(request, response);
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - cacheLoadedAtMillis < 30_000L && !cachedRules.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (now - cacheLoadedAtMillis < 30_000L && !cachedRules.isEmpty()) {
                return;
            }
            try {
                cachedRules = ruleRepository.listEnabledForMatching();
                cacheLoadedAtMillis = System.currentTimeMillis();
            } catch (Exception ex) {
                log.error("failed to load gw_api_rate_limit_rule", ex);
                cachedRules = List.of();
                cacheLoadedAtMillis = System.currentTimeMillis();
            }
        }
    }

    private GwApiRateLimitRule resolveRule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod() == null ? "" : request.getMethod().trim().toUpperCase();
        Long tid = null;
        var snap = TenantContextHolder.getOrNull();
        if (snap != null) {
            tid = snap.getTenantId();
        }
        for (GwApiRateLimitRule r : cachedRules) {
            if (r.getPathPattern() == null || r.getPathPattern().isBlank()) {
                continue;
            }
            if (r.getTenantId() != null && tid != null && !r.getTenantId().equals(tid)) {
                continue;
            }
            if (r.getTenantId() != null && tid == null) {
                continue;
            }
            if (!methodMatches(r.getHttpMethod(), method)) {
                continue;
            }
            if (MATCHER.match(r.getPathPattern(), uri)) {
                return r;
            }
        }
        return null;
    }

    private static boolean methodMatches(String ruleMethod, String requestMethod) {
        if (ruleMethod == null || ruleMethod.isBlank()) {
            return true;
        }
        String rm = ruleMethod.trim().toUpperCase();
        if ("*".equals(rm) || "ANY".equals(rm)) {
            return true;
        }
        return rm.equals(requestMethod);
    }

    private static String buildRateKey(HttpServletRequest request, GwApiRateLimitRule hit, long window) {
        Long tid = null;
        var snap = TenantContextHolder.getOrNull();
        if (snap != null) {
            tid = snap.getTenantId();
        }
        String ip = clientIp(request);
        if (tid != null) {
            return "t:" + tid + ":r:" + hit.getId() + ":w:" + window;
        }
        return "o:r:" + hit.getId() + ":ip:" + ip + ":w:" + window;
    }

    private static String clientIp(HttpServletRequest request) {
        String x = request.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) {
            return x.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        return m != null && HttpMethod.OPTIONS.matches(m);
    }
}
