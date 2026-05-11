package com.aaron.cloud.common.api.ports;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import java.util.function.Consumer;

public interface ModelInvokePort {

    void streamCompletion(ModelChatRequest request, Consumer<String> onToken) throws Exception;
}
