package com.aaron.cloud.common.gateway.openapi;

/** 从 OpenAPI 解析出的入参/出参 spec JSON（与 gw_api_endpoint 列格式一致）。 */
public record GwApiOpenApiSpecPair(String requestSpecJson, String responseSpecJson) {

    public static GwApiOpenApiSpecPair empty() {
        return new GwApiOpenApiSpecPair("[]", "[]");
    }
}
