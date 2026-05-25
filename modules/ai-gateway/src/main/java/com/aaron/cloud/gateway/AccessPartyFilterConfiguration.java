package com.aaron.cloud.gateway;

import com.aaron.cloud.common.gateway.GwAccessPartyGrantRepository;
import com.aaron.cloud.common.gateway.GwAccessPartyRepository;
import com.aaron.cloud.common.security.crypto.AesSecretCipher;
import com.aaron.cloud.gateway.accessparty.AccessPartyAuthFilter;
import com.aaron.cloud.gateway.accessparty.AccessPartyAuthzFilter;
import com.aaron.cloud.gateway.accessparty.AccessPartyCallLogFilter;
import com.aaron.cloud.gateway.accessparty.AccessPartyErrorWriter;
import com.aaron.cloud.gateway.accessparty.AccessPartyNonceStore;
import com.aaron.cloud.gateway.accessparty.AccessPartyRateLimitFilter;
import com.aaron.cloud.gateway.accessparty.AccessPartyRateLimitRedis;
import com.aaron.cloud.gateway.accessparty.AccessPartyRuntimeCatalog;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccessPartyFilterConfiguration {

    @Bean
    public FilterRegistrationBean<AccessPartyAuthFilter> accessPartyAuthFilterRegistration(
            AccessPartyRuntimeCatalog catalog,
            AccessPartyNonceStore nonceStore,
            AesSecretCipher secretCipher,
            AccessPartyErrorWriter errorWriter) {
        var reg = new FilterRegistrationBean<AccessPartyAuthFilter>();
        reg.setFilter(new AccessPartyAuthFilter(catalog, nonceStore, secretCipher, errorWriter));
        reg.addUrlPatterns("/*");
        reg.setOrder(12);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AccessPartyAuthzFilter> accessPartyAuthzFilterRegistration(
            AccessPartyRuntimeCatalog catalog, AccessPartyErrorWriter errorWriter) {
        var reg = new FilterRegistrationBean<AccessPartyAuthzFilter>();
        reg.setFilter(new AccessPartyAuthzFilter(catalog, errorWriter));
        reg.addUrlPatterns("/*");
        reg.setOrder(13);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AccessPartyRateLimitFilter> accessPartyRateLimitFilterRegistration(
            AccessPartyRateLimitRedis rateLimitRedis,
            AccessPartyErrorWriter errorWriter,
            com.aaron.cloud.common.gateway.GwAccessPartyRepository accessPartyRepository,
            com.aaron.cloud.common.gateway.GwAccessPartyGrantRepository grantRepository,
            com.aaron.cloud.common.gateway.GwApiEndpointRepository endpointRepository) {
        var reg = new FilterRegistrationBean<AccessPartyRateLimitFilter>();
        reg.setFilter(
                new AccessPartyRateLimitFilter(
                        rateLimitRedis,
                        errorWriter,
                        accessPartyRepository,
                        grantRepository,
                        endpointRepository));
        reg.addUrlPatterns("/*");
        reg.setOrder(14);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AccessPartyCallLogFilter> accessPartyCallLogFilterRegistration(
            com.aaron.cloud.common.gateway.GwAccessPartyCallLogRepository callLogRepository,
            com.aaron.cloud.common.gateway.GwApiEndpointRepository endpointRepository) {
        var reg = new FilterRegistrationBean<AccessPartyCallLogFilter>();
        reg.setFilter(new AccessPartyCallLogFilter(callLogRepository, endpointRepository));
        reg.addUrlPatterns("/*");
        reg.setOrder(15);
        return reg;
    }
}
