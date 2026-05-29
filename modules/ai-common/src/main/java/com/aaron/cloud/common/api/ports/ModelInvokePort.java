package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelStreamResult;
import java.util.function.Consumer;

public interface ModelInvokePort {

    void streamCompletion(ModelChatRequest request, Consumer<String> onToken) throws Exception;

    /** 流式补全并返回聚合结果（含 tool_calls / finish_reason）。 */
    ModelStreamResult streamCompletionWithResult(ModelChatRequest request, Consumer<String> onToken)
            throws Exception;
}
