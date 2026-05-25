package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.context.AccessPartyContextHolder;
import com.aaron.cloud.common.gateway.GwAccessPartyGrantRepository;
import com.aaron.cloud.common.gateway.GwAccessPartyRepository;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.gateway.entity.GwAccessPartyGrant;
import com.aaron.cloud.common.gateway.entity.GwApiEndpoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccessPartyRateLimitFilter extends OncePerRequestFilter {

    private final AccessPartyRateLimitRedis rateLimitRedis;
    private final AccessPartyErrorWriter errorWriter;
    private final GwAccessPartyRepository accessPartyRepository;
    private final GwAccessPartyGrantRepository grantRepository;
    private final com.aaron.cloud.common.gateway.GwApiEndpointRepository endpointRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!Boolean.TRUE.equals(request.getAttribute(AccessPartyRequestAttributes.ACCESS_PARTY_MODE))) {
            filterChain.doFilter(request, response);
            return;
        }
        Long endpointId = (Long) request.getAttribute(AccessPartyRequestAttributes.MATCHED_ENDPOINT_ID);
        Long grantId = (Long) request.getAttribute(AccessPartyRequestAttributes.MATCHED_GRANT_ID);
        var snap = AccessPartyContextHolder.getOrNull();
        if (snap == null || endpointId == null || grantId == null) {
            errorWriter.forbidden(response);
            return;
        }
        GwAccessPartyGrant grant = grantRepository.findById(grantId);
        GwAccessParty party = accessPartyRepository.findById(snap.getAccessPartyId());
        GwApiEndpoint endpoint = endpointRepository.findById(endpointId);
        if (grant == null || party == null || endpoint == null) {
            errorWriter.forbidden(response);
            return;
        }
        int perEndpointCap = Math.max(1, grant.getGrantedRpm());
        int totalCap = party.getTotalRpmCap() == null ? 0 : party.getTotalRpmCap();
        long window = System.currentTimeMillis() / 60_000L;
        String perKey = "ai:gw:ap:rpm:" + snap.getAccessPartyId() + ":" + endpointId + ":w:" + window;
        if (!rateLimitRedis.tryAcquire(perKey, perEndpointCap)) {
            errorWriter.rateLimited(response);
            return;
        }
        if (totalCap > 0) {
            String totalKey = "ai:gw:ap:rpm:total:" + snap.getAccessPartyId() + ":w:" + window;
            if (!rateLimitRedis.tryAcquire(totalKey, totalCap)) {
                errorWriter.rateLimited(response);
                return;
            }
        }
        Integer globalCap = endpoint.getGlobalRpmCap();
        if (globalCap != null && globalCap > 0) {
            String globalKey = "ai:gw:ap:rpm:global:" + endpointId + ":w:" + window;
            if (!rateLimitRedis.tryAcquire(globalKey, globalCap)) {
                errorWriter.rateLimited(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
