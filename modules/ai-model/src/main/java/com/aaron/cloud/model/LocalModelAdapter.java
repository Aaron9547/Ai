package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.dto.model.ModelStreamResult;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ai.remoting",
        name = "mode",
        havingValue = "local",
        matchIfMissing = true)
public class LocalModelAdapter implements ModelInvokePort {

    private final ModelApplicationService modelApplicationService;

    @Override
    public void streamCompletion(ModelChatRequest request, java.util.function.Consumer<String> onToken)
            throws Exception {
        modelApplicationService.streamCompletion(request, onToken);
    }

    @Override
    public ModelStreamResult streamCompletionWithResult(
            ModelChatRequest request, java.util.function.Consumer<String> onToken) throws Exception {
        return modelApplicationService.streamCompletionWithResult(request, onToken);
    }
}
