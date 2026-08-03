package com.aaron.cloud.notification.rest.internal;

import com.aaron.cloud.common.api.dto.message.MessageSendRequest;
import com.aaron.cloud.common.api.dto.message.MessageSendResult;
import com.aaron.cloud.notification.message.MessageSendApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/message")
@RequiredArgsConstructor
public class MessageInternalRestController {

    private final MessageSendApplicationService messageSendApplicationService;

    @PostMapping("/send")
    public MessageSendResult send(@Valid @RequestBody MessageSendRequest request) {
        return messageSendApplicationService.send(request);
    }
}
