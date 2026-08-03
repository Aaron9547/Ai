package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccessPartyAuthzFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final AccessPartyRuntimeCatalog catalog;
    private final AccessPartyErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!Boolean.TRUE.equals(request.getAttribute(AccessPartyRequestAttributes.ACCESS_PARTY_MODE))) {
            filterChain.doFilter(request, response);
            return;
        }
        var snap = com.aaron.cloud.common.context.AccessPartyContextHolder.getOrNull();
        if (snap == null) {
            errorWriter.unauthorized(response);
            return;
        }
        List<GwAccessPartyGrant> grants = catalog.listEnabledGrants(snap.getAccessPartyId());
        if (grants.isEmpty()) {
            errorWriter.forbidden(response);
            return;
        }
        String uri = request.getRequestURI();
        String method = request.getMethod() == null ? "" : request.getMethod().trim().toUpperCase();
        List<GwApiEndpoint> endpoints =
                catalog.listEnabledEndpoints().stream()
                        .sorted(
                                Comparator.comparing(
                                                (GwApiEndpoint e) ->
                                                        e.getPathPattern() == null
                                                                ? 0
                                                                : e.getPathPattern().length())
                                        .reversed())
                        .toList();
        for (GwAccessPartyGrant grant : grants) {
            if (grant.getGrantedRpm() == null || grant.getGrantedRpm() <= 0) {
                continue;
            }
            GwApiEndpoint endpoint =
                    endpoints.stream()
                            .filter(e -> e.getId().equals(grant.getEndpointId()))
                            .findFirst()
                            .orElse(null);
            if (endpoint == null || !methodMatches(endpoint.getHttpMethod(), method)) {
                continue;
            }
            if (MATCHER.match(endpoint.getPathPattern(), uri)) {
                request.setAttribute(AccessPartyRequestAttributes.MATCHED_ENDPOINT_ID, endpoint.getId());
                request.setAttribute(AccessPartyRequestAttributes.MATCHED_GRANT_ID, grant.getId());
                filterChain.doFilter(request, response);
                return;
            }
        }
        errorWriter.forbidden(response);
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        return m != null && HttpMethod.OPTIONS.matches(m);
    }
}
