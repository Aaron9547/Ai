package com.aaron.cloud.model;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import com.aaron.cloud.common.api.ports.ModelInvokePort;
import com.aaron.cloud.model.remote.RemoteModelClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
public class RemoteModelAdapter implements ModelInvokePort {

    private final RemoteModelClient remoteModelClient;

    @Override
    public void streamCompletion(ModelChatRequest request, java.util.function.Consumer<String> onToken) {
        String text = remoteModelClient.completion(request);
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                onToken.accept(String.valueOf(text.charAt(i)));
            }
        }
    }
}
