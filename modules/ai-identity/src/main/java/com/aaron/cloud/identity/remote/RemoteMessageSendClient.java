package com.aaron.cloud.identity.remote;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.dto.message.MessageSendResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ai-message",
        contextId = "remoteMessageSendClient",
        path = "/internal/v1/message",
        url = "${ai.remoting.message-base-url:http://127.0.0.1:8080}")
public interface RemoteMessageSendClient {

    @PostMapping("/send")
    MessageSendResult send(@RequestBody MessageSendRequest request);
}
