package com.aaron.cloud.common.api.dto.rag;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RagEmbedRequest {
    long tenantId;
    Long kbId;
    Long vectorModelIdOrNull;
    String text;
    boolean byVectorModelIdOrHash;
}
