package com.aaron.cloud.common.gateway.openapi;

import com.aaron.cloud.common.gateway.GwApiEndpointSpecSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.AntPathMatcher;

/** 将 SpringDoc OpenAPI 模型转为 gw_api_endpoint 的 request/response_spec_json。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GwApiOpenApiSpecExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public static GwApiOpenApiSpecPair extract(OpenAPI openApi, String pathPattern, String httpMethod) {
        Operation operation = findOperation(openApi, pathPattern, httpMethod);
        if (operation == null) {
            return GwApiOpenApiSpecPair.empty();
        }
        return extractFromOperation(openApi, operation);
    }

    /** 目录 path + method 是否在 OpenAPI 中存在对应操作（与 spec 是否为空无关）。 */
    public static boolean hasOperation(OpenAPI openApi, String pathPattern, String httpMethod) {
        return findOperation(openApi, pathPattern, httpMethod) != null;
    }

    public static Operation findOperation(OpenAPI openApi, String pathPattern, String httpMethod) {
        if (openApi == null || openApi.getPaths() == null || pathPattern == null || pathPattern.isBlank()) {
            return null;
        }
        String catalogPath = pathPattern.trim();
        String method = normalizeMethod(httpMethod);
        Operation wildcardMatch = null;
        for (Map.Entry<String, io.swagger.v3.oas.models.PathItem> entry : openApi.getPaths().entrySet()) {
            String openApiPath = entry.getKey();
            if (!pathMatches(catalogPath, openApiPath)) {
                continue;
            }
            io.swagger.v3.oas.models.PathItem item = entry.getValue();
            if (item == null) {
                continue;
            }
            Operation op = pickOperation(item, method);
            if (op == null) {
                continue;
            }
            if (openApiPathToAnt(openApiPath).equals(catalogPath)) {
                return op;
            }
            if (wildcardMatch == null) {
                wildcardMatch = op;
            }
        }
        return wildcardMatch;
    }

    public static GwApiOpenApiSpecPair extractFromOperation(OpenAPI openApi, Operation operation) {
        if (operation == null) {
            return GwApiOpenApiSpecPair.empty();
        }
        Components components = openApi == null ? null : openApi.getComponents();
        List<GwApiEndpointSpecSupport.ParamRow> requestRows = new ArrayList<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                if (parameter == null || parameter.getName() == null) {
                    continue;
                }
                GwApiEndpointSpecSupport.ParamRow row = new GwApiEndpointSpecSupport.ParamRow();
                row.setName(parameter.getName());
                row.setIn(parameter.getIn() == null ? "query" : parameter.getIn());
                row.setType(schemaType(resolveSchema(parameter.getSchema(), components)));
                row.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
                row.setDescription(parameter.getDescription());
                requestRows.add(row);
            }
        }
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            var media = firstMediaType(operation.getRequestBody().getContent());
            if (media != null && media.getSchema() != null) {
                appendSchemaProperties(
                        requestRows,
                        resolveSchema(media.getSchema(), components),
                        "body",
                        components,
                        "");
            }
        }
        List<GwApiEndpointSpecSupport.ParamRow> responseRows = new ArrayList<>();
        ApiResponse success = firstSuccessResponse(operation.getResponses());
        if (success != null && success.getContent() != null) {
            var media = firstMediaType(success.getContent());
            if (media != null && media.getSchema() != null) {
                appendSchemaProperties(
                        responseRows,
                        resolveSchema(media.getSchema(), components),
                        null,
                        components,
                        "");
            }
        }
        return new GwApiOpenApiSpecPair(toJson(requestRows), toJson(responseRows));
    }

    static boolean pathMatches(String catalogPath, String openApiPath) {
        String antFromOpenApi = openApiPathToAnt(openApiPath);
        if (catalogPath.equals(antFromOpenApi)) {
            return true;
        }
        if (PATH_MATCHER.match(catalogPath, antFromOpenApi)) {
            return true;
        }
        return PATH_MATCHER.match(antFromOpenApi, catalogPath);
    }

    static String openApiPathToAnt(String openApiPath) {
        if (openApiPath == null) {
            return "";
        }
        return openApiPath.replaceAll("\\{[^/}]+\\}", "*");
    }

    private static Operation pickOperation(io.swagger.v3.oas.models.PathItem item, String method) {
        if ("*".equals(method) || "ANY".equals(method)) {
            if (item.getGet() != null) return item.getGet();
            if (item.getPost() != null) return item.getPost();
            if (item.getPut() != null) return item.getPut();
            if (item.getDelete() != null) return item.getDelete();
            if (item.getPatch() != null) return item.getPatch();
            return null;
        }
        return switch (method) {
            case "GET" -> item.getGet();
            case "POST" -> item.getPost();
            case "PUT" -> item.getPut();
            case "DELETE" -> item.getDelete();
            case "PATCH" -> item.getPatch();
            case "HEAD" -> item.getHead();
            case "OPTIONS" -> item.getOptions();
            default -> null;
        };
    }

    private static ApiResponse firstSuccessResponse(Map<String, ApiResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return null;
        }
        for (String code : List.of("200", "201", "202", "204", "default")) {
            ApiResponse response = responses.get(code);
            if (response != null) {
                return response;
            }
        }
        return responses.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static io.swagger.v3.oas.models.media.MediaType firstMediaType(
            Map<String, io.swagger.v3.oas.models.media.MediaType> content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        io.swagger.v3.oas.models.media.MediaType json = content.get("application/json");
        if (json != null) {
            return json;
        }
        io.swagger.v3.oas.models.media.MediaType sse = content.get("text/event-stream");
        if (sse != null) {
            return sse;
        }
        return content.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    @SuppressWarnings("rawtypes")
    private static void appendSchemaProperties(
            List<GwApiEndpointSpecSupport.ParamRow> rows,
            Schema schema,
            String paramIn,
            Components components,
            String prefix) {
        if (schema == null) {
            return;
        }
        schema = resolveSchema(schema, components);
        if (schema == null) {
            return;
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            for (Object rawKey : schema.getProperties().keySet()) {
                String name = String.valueOf(rawKey);
                Object rawProperty = schema.getProperties().get(rawKey);
                if (!(rawProperty instanceof Schema propertySchema)) {
                    continue;
                }
                String fullName = prefix == null || prefix.isBlank() ? name : prefix + "." + name;
                Schema resolved = resolveSchema(propertySchema, components);
                if (resolved != null
                        && "object".equalsIgnoreCase(String.valueOf(resolved.getType()))
                        && resolved.getProperties() != null
                        && !resolved.getProperties().isEmpty()) {
                    appendSchemaProperties(rows, resolved, paramIn, components, fullName);
                    continue;
                }
                GwApiEndpointSpecSupport.ParamRow row = new GwApiEndpointSpecSupport.ParamRow();
                row.setName(fullName);
                if (paramIn != null) {
                    row.setIn(paramIn);
                }
                row.setType(schemaType(resolved));
                row.setRequired(isRequired(schema, name));
                if (resolved != null) {
                    row.setDescription(resolved.getDescription());
                }
                rows.add(row);
            }
            return;
        }
        if (schema.getAdditionalProperties() instanceof Schema ap) {
            appendLooseSchemaRow(rows, resolveSchema(ap, components), paramIn, prefix, "mapValue", "Map 键值");
            return;
        }
        if ("array".equalsIgnoreCase(String.valueOf(schema.getType())) && schema.getItems() != null) {
            appendLooseSchemaRow(
                    rows,
                    resolveSchema(schema.getItems(), components),
                    paramIn,
                    prefix,
                    prefix == null || prefix.isBlank() ? "items" : prefix + "[]",
                    "数组元素");
            return;
        }
        if (prefix != null && !prefix.isBlank()) {
            return;
        }
        appendLooseSchemaRow(rows, schema, paramIn, "", "body", schema.getDescription());
    }

    @SuppressWarnings("rawtypes")
    private static void appendLooseSchemaRow(
            List<GwApiEndpointSpecSupport.ParamRow> rows,
            Schema schema,
            String paramIn,
            String prefix,
            String defaultName,
            String defaultDescription) {
        if (schema == null) {
            return;
        }
        GwApiEndpointSpecSupport.ParamRow row = new GwApiEndpointSpecSupport.ParamRow();
        row.setName(prefix == null || prefix.isBlank() ? defaultName : prefix);
        if (paramIn != null) {
            row.setIn(paramIn);
        }
        row.setType(schemaType(schema));
        row.setRequired(Boolean.TRUE.equals(schema.getRequired()));
        row.setDescription(
                schema.getDescription() != null && !schema.getDescription().isBlank()
                        ? schema.getDescription()
                        : defaultDescription);
        rows.add(row);
    }

    @SuppressWarnings("rawtypes")
    private static boolean isRequired(Schema parent, String propertyName) {
        if (parent == null || parent.getRequired() == null) {
            return false;
        }
        return parent.getRequired().contains(propertyName);
    }

    @SuppressWarnings("rawtypes")
    private static Schema resolveSchema(Schema schema, Components components) {
        if (schema == null) {
            return null;
        }
        if (schema.get$ref() != null && components != null && components.getSchemas() != null) {
            String refName = schema.get$ref().replace("#/components/schemas/", "");
            Schema resolved = components.getSchemas().get(refName);
            if (resolved != null) {
                return resolveSchema(resolved, components);
            }
        }
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            for (Object raw : schema.getAllOf()) {
                if (raw instanceof Schema part) {
                    Schema resolved = resolveSchema(part, components);
                    if (resolved != null
                            && resolved.getProperties() != null
                            && !resolved.getProperties().isEmpty()) {
                        return resolved;
                    }
                }
            }
        }
        if (schema.getItems() != null && (schema.getProperties() == null || schema.getProperties().isEmpty())) {
            return schema;
        }
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static String schemaType(Schema schema) {
        if (schema == null) {
            return "string";
        }
        if (schema.getType() != null) {
            if ("array".equalsIgnoreCase(schema.getType()) && schema.getItems() != null) {
                return "array<" + schemaType(schema.getItems()) + ">";
            }
            return schema.getType();
        }
        if (schema.get$ref() != null) {
            return schema.get$ref().replace("#/components/schemas/", "");
        }
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            return "object";
        }
        return "string";
    }

    private static String normalizeMethod(String httpMethod) {
        if (httpMethod == null || httpMethod.isBlank()) {
            return "*";
        }
        return httpMethod.trim().toUpperCase(Locale.ROOT);
    }

    private static String toJson(List<GwApiEndpointSpecSupport.ParamRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(rows);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
