package com.aaron.cloud.common.api.dto.rag;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RagQuerySnippetsRequest {
    Long tenantId;
    List<Long> kbIds;
    Long kbId;
    String query;
    int topK;
    boolean acrossKnowledgeBases;
}
