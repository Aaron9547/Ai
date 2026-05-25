package com.aaron.cloud.common.gateway.openapi;

import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.service.OpenAPIService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 运行时从 SpringDoc OpenAPI 解析接口入参/出参 spec（导出 fallback 与目录同步共用）。 */
@Service
@RequiredArgsConstructor
public class GwApiOpenApiCatalogService {

    private final ObjectProvider<OpenAPIService> openApiServiceProvider;

    public Optional<GwApiOpenApiSpecPair> resolve(String pathPattern, String httpMethod) {
        OpenAPIService openApiService = openApiServiceProvider.getIfAvailable();
        if (openApiService == null) {
            return Optional.empty();
        }
        OpenAPI openApi = loadOpenApi(openApiService);
        if (openApi == null) {
            return Optional.empty();
        }
        GwApiOpenApiSpecPair pair = GwApiOpenApiSpecExtractor.extract(openApi, pathPattern, httpMethod);
        if (GwApiEndpointSpecSupport.isBlankSpec(pair.requestSpecJson())
                && GwApiEndpointSpecSupport.isBlankSpec(pair.responseSpecJson())) {
            return Optional.empty();
        }
        return Optional.of(pair);
    }

    public String effectiveRequestSpecJson(String storedRequestSpecJson, String pathPattern, String httpMethod) {
        if (!GwApiEndpointSpecSupport.isBlankSpec(storedRequestSpecJson)) {
            return storedRequestSpecJson;
        }
        return resolve(pathPattern, httpMethod)
                .map(GwApiOpenApiSpecPair::requestSpecJson)
                .filter(json -> !GwApiEndpointSpecSupport.isBlankSpec(json))
                .orElse(storedRequestSpecJson);
    }

    public String effectiveResponseSpecJson(String storedResponseSpecJson, String pathPattern, String httpMethod) {
        if (!GwApiEndpointSpecSupport.isBlankSpec(storedResponseSpecJson)) {
            return storedResponseSpecJson;
        }
        return resolve(pathPattern, httpMethod)
                .map(GwApiOpenApiSpecPair::responseSpecJson)
                .filter(json -> !GwApiEndpointSpecSupport.isBlankSpec(json))
                .orElse(storedResponseSpecJson);
    }

    private static OpenAPI loadOpenApi(OpenAPIService openApiService) {
        Locale locale = Locale.getDefault();
        OpenAPI cached = openApiService.getCachedOpenAPI(locale);
        if (cached != null) {
            return cached;
        }
        return openApiService.build(locale);
    }
}
