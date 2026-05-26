package com.aaron.cloud.common.gateway.openapi;

import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;

/** OpenAPI 与目录行对齐结果（区分「无操作」与「有操作但无 schema」）。 */
public record GwApiOpenApiLookupResult(boolean operationFound, GwApiOpenApiSpecPair spec) {

    public static GwApiOpenApiLookupResult notFound() {
        return new GwApiOpenApiLookupResult(false, GwApiOpenApiSpecPair.empty());
    }

    public static GwApiOpenApiLookupResult found(GwApiOpenApiSpecPair spec) {
        return new GwApiOpenApiLookupResult(true, spec == null ? GwApiOpenApiSpecPair.empty() : spec);
    }

    public boolean hasExtractableSpec() {
        if (spec == null) {
            return false;
        }
        return !GwApiEndpointSpecSupport.isBlankSpec(spec.requestSpecJson())
                || !GwApiEndpointSpecSupport.isBlankSpec(spec.responseSpecJson());
    }
}
