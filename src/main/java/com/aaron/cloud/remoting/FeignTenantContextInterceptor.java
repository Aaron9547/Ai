package com.aaron.cloud.remoting;

import com.aaron.cloud.common.context.AccessPartyContextHolder;
import com.aaron.cloud.common.context.TenantContextHolder;
import com.aaron.cloud.common.gateway.AccessPartyHttpHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
public class FeignTenantContextInterceptor {

    @Bean
    public RequestInterceptor tenantContextRequestInterceptor() {
        return (RequestTemplate template) -> {
            var snap = TenantContextHolder.getOrNull();
            if (snap == null) {
                return;
            }
            if (snap.getTenantId() != null) {
                template.header("X-Tenant-Id", snap.getTenantId().toString());
            }
            if (snap.getUserId() != null) {
                template.header("X-User-Id", snap.getUserId().toString());
            }
            if (snap.getDeviceId() != null && !snap.getDeviceId().isBlank()) {
                template.header("X-Device-Id", snap.getDeviceId());
            }
            var ap = AccessPartyContextHolder.getOrNull();
            if (ap != null) {
                if (ap.getAccessPartyId() != null) {
                    template.header(AccessPartyHttpHeaders.ACCESS_PARTY_ID, ap.getAccessPartyId().toString());
                }
                if (ap.getAppId() != null && !ap.getAppId().isBlank()) {
                    template.header(AccessPartyHttpHeaders.ACCESS_PARTY_APP_ID, ap.getAppId());
                }
            }
        };
    }
}
