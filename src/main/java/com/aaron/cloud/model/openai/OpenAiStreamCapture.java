package com.aaron.cloud.model.openai;

import com.aaron.cloud.common.api.dto.model.ModelStreamResult;
import com.aaron.cloud.common.api.dto.model.ModelToolCall;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/** 单次 OpenAI 兼容 SSE 流解析产物。 */
@Getter
public final class OpenAiStreamCapture {

    private final StringBuilder content = new StringBuilder();
    private final ToolCallDeltaAccumulator toolCallAccumulator = new ToolCallDeltaAccumulator();
    private String finishReason;

    public void appendContent(String token) {
        if (token != null && !token.isEmpty()) {
            content.append(token);
        }
    }

    public void setFinishReason(String finishReason) {
        if (finishReason != null && !finishReason.isBlank()) {
            this.finishReason = finishReason;
        }
    }

    public ModelStreamResult toResult() {
        List<ModelToolCall> tools = toolCallAccumulator.build();
        return ModelStreamResult.builder()
                .content(content.toString())
                .finishReason(finishReason)
                .toolCalls(tools == null ? new ArrayList<>() : tools)
                .build();
    }
}
