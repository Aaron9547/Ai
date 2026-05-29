package com.aaron.cloud.common.api.dto.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStreamResult {

    private String content;
    private String finishReason;
    @Builder.Default
    private List<ModelToolCall> toolCalls = new ArrayList<>();

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
