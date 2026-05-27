package com.aaron.cloud.gateway.accessparty;

import com.aaron.cloud.common.gateway.AccessPartyHttpHeaders;
import com.aaron.cloud.common.context.AccessPartyContextHolder;
import com.aaron.cloud.common.context.AccessPartySnapshot;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.context.TenantSnapshot;
import com.aaron.cloud.common.gateway.entity.GwAccessParty;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccessPartyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessPartyAuthFilter.class);

    private final AccessPartyRuntimeCatalog catalog;
    private final AccessPartyNonceStore nonceStore;
    private final AesSecretCipher secretCipher;
    private final AccessPartyErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!AccessPartyFilterSkipPolicy.requiresAccessPartyChain(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String appId = request.getHeader(AccessPartyHttpHeaders.APP_ID);
        String timestamp = request.getHeader(AccessPartyHttpHeaders.TIMESTAMP);
        String nonce = request.getHeader(AccessPartyHttpHeaders.NONCE);
        String signature = request.getHeader(AccessPartyHttpHeaders.SIGNATURE);
        if (appId == null
                || appId.isBlank()
                || timestamp == null
                || nonce == null
                || signature == null) {
            errorWriter.unauthorized(response);
            return;
        }
        if (!AccessPartyHmacSupport.verifyTimestamp(timestamp)) {
            errorWriter.unauthorized(response);
            return;
        }
        if (!nonceStore.tryConsume(appId, nonce)) {
            errorWriter.unauthorized(response);
            return;
        }
        GwAccessParty party = catalog.findActivePartyByAppId(appId);
        if (party == null) {
            errorWriter.unauthorized(response);
            return;
        }
        byte[] body = CachedBodyHttpServletRequest.readBodyBytes(request);
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);
        String pathWithQuery = AccessPartyHmacSupport.buildCanonicalPathWithQuery(wrapped);
        String canonical =
                AccessPartyHmacSupport.buildCanonicalString(
                        wrapped.getMethod(), pathWithQuery, timestamp.trim(), nonce.trim(), body);
        String secretPlain;
        try {
            secretPlain = secretCipher.decryptFromBase64(party.getSecretCipher());
        } catch (Exception ex) {
            log.warn("access party secret decrypt failed appId={}", appId, ex);
            errorWriter.unauthorized(response);
            return;
        }
        if (!AccessPartyHmacSupport.verifySignature(secretPlain, canonical, signature)) {
            errorWriter.unauthorized(response);
            return;
        }
        Long headerTenant = parseTenantHeader(wrapped);
        if (headerTenant != null && !headerTenant.equals(party.getTenantId())) {
            errorWriter.forbidden(response);
            return;
        }
        AccessPartySnapshot apSnap =
                AccessPartySnapshot.builder()
                        .accessPartyId(party.getId())
                        .tenantId(party.getTenantId())
                        .appId(party.getAppId())
                        .displayName(party.getDisplayName())
                        .build();
        AccessPartyContextHolder.set(apSnap);
        var tenantSnap =
                TenantSnapshot.builder()
                        .tenantId(party.getTenantId())
                        .build();
        TenantContextHolder.set(tenantSnap);
        wrapped.setAttribute(AccessPartyRequestAttributes.ACCESS_PARTY_MODE, Boolean.TRUE);
        filterChain.doFilter(wrapped, response);
    }

    private static Long parseTenantHeader(HttpServletRequest request) {
        String v = request.getHeader("X-Tenant-Id");
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
