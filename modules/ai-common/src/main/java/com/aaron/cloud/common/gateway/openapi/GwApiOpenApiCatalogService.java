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

    /** 加载（或构建）当前 SpringDoc OpenAPI 模型；全量同步时应只调用一次。 */
    public Optional<OpenAPI> openApiCatalog() {
        OpenAPIService openApiService = openApiServiceProvider.getIfAvailable();
        if (openApiService == null) {
            return Optional.empty();
        }
        OpenAPI openApi = loadOpenApi(openApiService);
        return openApi == null ? Optional.empty() : Optional.of(openApi);
    }

    public int openApiPathCount(OpenAPI openApi) {
        if (openApi == null || openApi.getPaths() == null) {
            return 0;
        }
        return openApi.getPaths().size();
    }

    public GwApiOpenApiLookupResult lookup(OpenAPI openApi, String pathPattern, String httpMethod) {
        if (openApi == null || openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            return GwApiOpenApiLookupResult.notFound();
        }
        if (!GwApiOpenApiSpecExtractor.hasOperation(openApi, pathPattern, httpMethod)) {
            return GwApiOpenApiLookupResult.notFound();
        }
        GwApiOpenApiSpecPair pair = GwApiOpenApiSpecExtractor.extract(openApi, pathPattern, httpMethod);
        return GwApiOpenApiLookupResult.found(pair);
    }

    public Optional<GwApiOpenApiSpecPair> resolve(String pathPattern, String httpMethod) {
        return openApiCatalog()
                .flatMap(openApi -> resolve(openApi, pathPattern, httpMethod));
    }

    public Optional<GwApiOpenApiSpecPair> resolve(OpenAPI openApi, String pathPattern, String httpMethod) {
        GwApiOpenApiLookupResult lookup = lookup(openApi, pathPattern, httpMethod);
        if (!lookup.hasExtractableSpec()) {
            return Optional.empty();
        }
        return Optional.of(lookup.spec());
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
        OpenAPI built = openApiService.build(locale);
        if (hasPaths(built)) {
            return built;
        }
        OpenAPI cached = openApiService.getCachedOpenAPI(locale);
        if (hasPaths(cached)) {
            return cached;
        }
        return built;
    }

    private static boolean hasPaths(OpenAPI openApi) {
        return openApi != null && openApi.getPaths() != null && !openApi.getPaths().isEmpty();
    }
}
