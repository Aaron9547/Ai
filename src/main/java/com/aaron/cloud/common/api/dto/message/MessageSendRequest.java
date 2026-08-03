package com.aaron.cloud.common.api.dto.message;

import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageSendRequest {

    long tenantId;
    MessageSceneCode sceneCode;
    String recipient;
    Map<String, String> templateVars;
    String idempotencyKey;
    @Builder.Default boolean async = true;
}
