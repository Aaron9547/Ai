package com.aaron.cloud.common.gateway.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GwApiOpenApiSpecExtractorTest {

    @Test
    void pathMatchesOpenApiTemplateToAntPattern() {
        assertTrue(GwApiOpenApiSpecExtractor.pathMatches(
                "/partner/v1/chat/probe", "/partner/v1/chat/probe"));
        assertTrue(GwApiOpenApiSpecExtractor.pathMatches(
                "/api/v1/admin/gateway-access-parties/*", "/api/v1/admin/gateway-access-parties/{id}"));
        assertTrue(GwApiOpenApiSpecExtractor.pathMatches(
                "/api/v1/admin/gateway-api-endpoints/sync-openapi-spec/batch",
                "/api/v1/admin/gateway-api-endpoints/sync-openapi-spec/batch"));
    }

    @Test
    void extractRequestAndResponseFromOperation() {
        OpenAPI openApi = new OpenAPI();
        Operation operation = new Operation();
        Parameter query = new Parameter();
        query.setName("page");
        query.setIn("query");
        query.setRequired(false);
        query.setSchema(new Schema<>().type("integer"));
        query.setDescription("页码");
        operation.setParameters(List.of(query));

        Schema<?> bodySchema = new Schema<>();
        bodySchema.setType("object");
        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("displayName", new Schema<>().type("string").description("名称"));
        bodySchema.setProperties(props);
        bodySchema.setRequired(List.of("displayName"));
        Content requestContent = new Content();
        requestContent.addMediaType("application/json", new MediaType().schema(bodySchema));
        operation.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody().content(requestContent));

        Schema<?> responseSchema = new Schema<>();
        responseSchema.setType("object");
        Map<String, Schema> respProps = new LinkedHashMap<>();
        respProps.put("status", new Schema<>().type("string").description("ok"));
        responseSchema.setProperties(respProps);
        Content responseContent = new Content();
        responseContent.addMediaType("application/json", new MediaType().schema(responseSchema));
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", new ApiResponse().content(responseContent));
        operation.setResponses(responses);

        PathItem pathItem = new PathItem();
        pathItem.setGet(operation);
        openApi.setPaths(Map.of("/partner/v1/demo", pathItem));

        GwApiOpenApiSpecPair pair =
                GwApiOpenApiSpecExtractor.extract(openApi, "/partner/v1/demo", "GET");

        assertFalse(GwApiEndpointSpecSupport.isBlankSpec(pair.requestSpecJson()));
        assertFalse(GwApiEndpointSpecSupport.isBlankSpec(pair.responseSpecJson()));
        assertTrue(pair.requestSpecJson().contains("displayName"));
        assertTrue(pair.responseSpecJson().contains("status"));
    }

    @Test
    void extractReturnsEmptyWhenNoMatch() {
        OpenAPI openApi = new OpenAPI();
        openApi.setPaths(Map.of("/other", new PathItem().get(new Operation())));
        GwApiOpenApiSpecPair pair =
                GwApiOpenApiSpecExtractor.extract(openApi, "/partner/v1/health", "GET");
        assertEquals("[]", pair.requestSpecJson());
        assertEquals("[]", pair.responseSpecJson());
    }
}
