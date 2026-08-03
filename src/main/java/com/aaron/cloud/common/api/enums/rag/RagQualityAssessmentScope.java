package com.aaron.cloud.common.api.enums.rag;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** RAG 质量评测范围（落库 {@code rag_quality_assessment.scope}）。 */
@Getter
@RequiredArgsConstructor
public enum RagQualityAssessmentScope {
    MESSAGE_TURN("MESSAGE_TURN"),
    CHUNK_QUERY("CHUNK_QUERY");

    @EnumValue
    private final String code;
}
