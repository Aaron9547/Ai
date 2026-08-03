package com.aaron.cloud.common.api.dto.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelToolDefinition {

    private String type;
    private FunctionDef function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDef {
        private String name;
        private String description;
        private JsonNode parameters;
    }
}
