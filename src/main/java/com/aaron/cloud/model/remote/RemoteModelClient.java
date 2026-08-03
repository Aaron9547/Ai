package com.aaron.cloud.model.remote;

import com.aaron.cloud.common.api.dto.model.ModelChatRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ai-model",
        contextId = "remoteModelClient",
        path = "/internal/model",
        url = "${ai.remoting.model-base-url:http://127.0.0.1:8080}")
public interface RemoteModelClient {

    @PostMapping("/completion")
    String completion(@RequestBody ModelChatRequest request);
}
