package com.aaron.cloud.gateway;

import com.aaron.cloud.common.web.http.AdminUiNegotiationHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 与 {@code /api/**}、{@code /open/**} 对齐的 CORS；允许来源以库表 {@code gw_cors_allowed_origin} 为真源（见 {@link CorsAllowedOriginApplicationService}）。
 */
@Configuration
@RequiredArgsConstructor
public class DynamicCorsConfiguration {

    private final CorsAllowedOriginApplicationService corsAllowedOriginApplicationService;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return (HttpServletRequest request) -> {
            String uri = request.getRequestURI();
            if (uri == null || (!uri.startsWith("/api/") && !uri.startsWith("/open/"))) {
                return null;
            }
            List<String> origins = corsAllowedOriginApplicationService.resolveEffectiveOriginsForRequest();
            if (origins.isEmpty()) {
                return null;
            }
            CorsConfiguration c = new CorsConfiguration();
            c.setAllowedOrigins(origins);
            c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            c.setAllowedHeaders(List.of("*"));
            c.setAllowCredentials(true);
            c.setMaxAge(3600L);
            c.setExposedHeaders(
                    List.of(
                            HttpHeaders.CONTENT_LANGUAGE,
                            HttpHeaders.VARY,
                            AdminUiNegotiationHeaders.X_LLM_META_LOCALE,
                            AdminUiNegotiationHeaders.X_LLM_META_LANG_PARAM));
            return c;
        };
    }
}
