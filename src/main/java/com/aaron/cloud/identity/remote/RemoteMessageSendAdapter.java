package com.aaron.cloud.identity.remote;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.dto.message.MessageSendResult;
import com.aaron.cloud.common.api.ports.MessageSendPort;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.remoting", name = "mode", havingValue = "remote")
public class RemoteMessageSendAdapter implements MessageSendPort {

    private final RemoteMessageSendClient remoteMessageSendClient;

    @Override
    public MessageSendResult send(MessageSendRequest request) {
        try {
            return remoteMessageSendClient.send(request);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "消息中心不可用", e);
        }
    }
}
