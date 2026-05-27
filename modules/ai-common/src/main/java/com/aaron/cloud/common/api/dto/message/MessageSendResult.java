package com.aaron.cloud.common.api.dto.message;

import com.aaron.cloud.common.api.enums.message.MessageDeliveryStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageSendResult {

    long deliveryLogId;
    MessageDeliveryStatus status;
    String providerMsgId;
    String errorMessage;
}
