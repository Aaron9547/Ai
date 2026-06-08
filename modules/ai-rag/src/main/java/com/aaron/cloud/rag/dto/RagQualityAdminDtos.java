package com.aaron.cloud.rag.dto;

import com.aaron.cloud.common.api.enums.eval.EvalRunStatus;
import com.aaron.cloud.common.api.enums.rag.RagQualityAssessmentScope;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RagQualityAdminDtos {

    private RagQualityAdminDtos() {}

    public record RagQualityAssessmentSubmitRequest(
            RagQualityAssessmentScope scope,
            Long conversationId,
            Long userMessageId,
            Long assistantMessageId,
            Long kbId,
            Long chunkId,
            String queryText,
            String assistantAnswer) {}

    public record RagQualityAssessmentView(
            String runId,
            RagQualityAssessmentScope scope,
            EvalRunStatus status,
            Long conversationId,
            Long userMessageId,
            Long assistantMessageId,
            Long kbId,
            Long chunkId,
            String queryText,
            BigDecimal recallHitRate,
            BigDecimal citationAccuracy,
            BigDecimal faithfulnessScore,
            String resultJson,
            String errorCode,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime finishedAt,
            String conversationTitle,
            String kbName,
            String chunkLabel) {}
}
