package com.aaron.cloud.model.spi;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import java.util.function.Consumer;

public interface ModelCompletionEngine {

    void streamCompletion(ModelChatRequest request, Consumer<String> onToken) throws Exception;
}
