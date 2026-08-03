package com.aaron.cloud.common.api.dto.job;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class JobDispatchMessage {
    String traceId;
    Long tenantId;
    String deviceId;
    Long userId;
    long jobTaskId;
}
