package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.context.AccessPartyContextHolder;
import com.aaron.cloud.common.gateway.GwAccessPartyCallLogRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyCallLog;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import com.aaron.cloud.common.time.BeijingTime;
import com.aaron.cloud.common.web.HttpClientIp;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class AccessPartyCallLogFilter extends OncePerRequestFilter {

    private final GwAccessPartyCallLogRepository callLogRepository;
    private final com.aaron.cloud.common.gateway.GwApiEndpointRepository endpointRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!Boolean.TRUE.equals(request.getAttribute(AccessPartyRequestAttributes.ACCESS_PARTY_MODE))) {
            filterChain.doFilter(request, response);
            return;
        }
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            var snap = AccessPartyContextHolder.getOrNull();
            if (snap == null) {
                return;
            }
            Long endpointId = (Long) request.getAttribute(AccessPartyRequestAttributes.MATCHED_ENDPOINT_ID);
            GwApiEndpoint endpoint = endpointId == null ? null : endpointRepository.findById(endpointId);
            var row = new GwAccessPartyCallLog();
            row.setTenantId(snap.getTenantId());
            row.setAccessPartyId(snap.getAccessPartyId());
            row.setEndpointId(endpointId);
            row.setMethod(request.getMethod());
            row.setPathPattern(request.getRequestURI());
            row.setHttpStatus(response.getStatus());
            row.setDurationMs(durationMs);
            row.setClientIp(HttpClientIp.resolve(request));
            row.setTokensConsumed(0L);
            if (endpoint != null) {
                row.setInterfaceKind(endpoint.getInterfaceKind());
            }
            if (response.getStatus() == 429) {
                row.setErrorCode(com.aaron.cloud.common.api.ErrorCodes.ACCESS_PARTY_RATE_LIMITED);
            } else if (response.getStatus() == 403) {
                row.setErrorCode(com.aaron.cloud.common.api.ErrorCodes.ACCESS_PARTY_ENDPOINT_DENIED);
            } else if (response.getStatus() == 401) {
                row.setErrorCode(com.aaron.cloud.common.api.ErrorCodes.ACCESS_PARTY_UNAUTHORIZED);
            }
            row.setTraceId(request.getHeader("X-Request-Id"));
            row.setCreatedAt(BeijingTime.nowLocal());
            try {
                callLogRepository.insert(row);
            } catch (Exception ex) {
                log.warn("access party call log insert failed path={}", request.getRequestURI(), ex);
            }
        }
    }
}
