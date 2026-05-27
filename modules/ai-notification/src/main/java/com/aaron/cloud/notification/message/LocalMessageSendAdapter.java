package com.aaron.cloud.notification.message;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.dto.message.MessageSendResult;
import com.aaron.cloud.common.api.ports.MessageSendPort;
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
public class LocalMessageSendAdapter implements MessageSendPort {

    private final MessageSendApplicationService messageSendApplicationService;

    @Override
    public MessageSendResult send(MessageSendRequest request) {
        return messageSendApplicationService.send(request);
    }
}
