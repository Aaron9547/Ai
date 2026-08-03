package com.aaron.cloud.common.rag.entity;

import com.aaron.cloud.common.api.enums.eval.EvalRunStatus;
import com.aaron.cloud.common.api.enums.rag.RagQualityAssessmentScope;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rag_quality_assessment")
public class RagQualityAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;
    private Long tenantId;
    private RagQualityAssessmentScope scope;
    private EvalRunStatus status;
    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long kbId;
    private Long chunkId;
    private String queryText;
    private String assistantAnswer;
    private Long triggeredByAdminId;
    private BigDecimal recallHitRate;
    private BigDecimal citationAccuracy;
    private BigDecimal faithfulnessScore;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    /** 管理端展示字段，非表列。 */
    @TableField(exist = false)
    private String conversationTitle;

    @TableField(exist = false)
    private String kbName;

    @TableField(exist = false)
    private String chunkLabel;
}
