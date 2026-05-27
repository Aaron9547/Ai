package com.aaron.cloud.common.api.dto.message;

import com.aaron.cloud.common.api.enums.message.MessageSceneCode;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDispatchMessage {

    private long deliveryLogId;
    private long tenantId;
    private MessageSceneCode sceneCode;
    private String recipient;
    private Map<String, String> templateVars;
    private String idempotencyKey;
    private int attemptCount;
}
