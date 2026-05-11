package com.aaron.cloud.gateway;

import com.aaron.cloud.common.accesslog.SysHttpAccessLogRepository;
import com.aaron.cloud.common.security.SecUserAccountRepository;
import com.aaron.cloud.common.tenant.SysTenantRepository;
import com.aaron.cloud.common.gateway.GwApiRateLimitRuleRepository;
import com.aaron.cloud.identity.admin.AdminMenuAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantFilterConfiguration {

    /**
     * 在 Spring Security（order 约 -100）之后执行，以便读取已认证的 {@link org.springframework.security.oauth2.jwt.Jwt}。
     */
    @Bean
    public FilterRegistrationBean<JwtSessionGateFilter> jwtSessionGateFilterRegistration(SecUserAccountRepository userAccountRepository) {
        var reg = new FilterRegistrationBean<JwtSessionGateFilter>();
        reg.setFilter(new JwtSessionGateFilter(userAccountRepository));
        reg.addUrlPatterns("/*");
        reg.setOrder(9);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            SysTenantRepository tenantRepository, @Value("${ai.tenant.default-id:1}") long defaultTenantId) {
        var filter = new TenantContextFilter(tenantRepository, defaultTenantId);
        var reg = new FilterRegistrationBean<TenantContextFilter>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/*");
        reg.setOrder(10);
        return reg;
    }

    /**
     * 紧接 {@link TenantContextFilter} 之后，保证 {@link AccessLogFilter} 的 finally 执行时租户上下文尚未被清除。
     */
    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(SysHttpAccessLogRepository accessLogRepository) {
        var reg = new FilterRegistrationBean<AccessLogFilter>();
        reg.setFilter(new AccessLogFilter(accessLogRepository));
        reg.addUrlPatterns("/*");
        reg.setOrder(11);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AdminMemberDenyFilter> adminMemberDenyFilterRegistration() {
        var reg = new FilterRegistrationBean<AdminMemberDenyFilter>();
        reg.setFilter(new AdminMemberDenyFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(11);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AdminMenuAuthorizationFilter> adminMenuAuthorizationFilterRegistration(
            AdminMenuAuthorizationService adminMenuAuthorizationService) {
        var reg = new FilterRegistrationBean<AdminMenuAuthorizationFilter>();
        reg.setFilter(new AdminMenuAuthorizationFilter(adminMenuAuthorizationService));
        reg.addUrlPatterns("/*");
        reg.setOrder(12);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilterRegistration(GwApiRateLimitRuleRepository ruleRepository) {
        var reg = new FilterRegistrationBean<ApiRateLimitFilter>();
        reg.setFilter(new ApiRateLimitFilter(ruleRepository));
        reg.addUrlPatterns("/*");
        reg.setOrder(14);
        return reg;
    }
}
