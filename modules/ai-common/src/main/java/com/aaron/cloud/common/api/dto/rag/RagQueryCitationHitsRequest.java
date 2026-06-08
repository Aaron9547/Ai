package com.aaron.cloud.common.api.dto.rag;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalProfile;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RagQueryCitationHitsRequest {
    Long tenantId;
    List<Long> kbIds;
    Long kbId;
    String query;
    int topK;
    boolean acrossKnowledgeBases;
    /** null 时按 {@link RagRetrievalProfile#COMPLEX_HYBRID} 处理。 */
    RagRetrievalProfile profile;
}
