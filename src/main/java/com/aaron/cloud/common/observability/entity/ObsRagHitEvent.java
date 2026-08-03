package com.aaron.cloud.common.observability.entity;

import com.aaron.cloud.common.api.enums.rag.RagRetrievalHitSource;
import com.aaron.cloud.common.api.enums.rag.RagRetrievalMode;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("obs_rag_hit_event")
public class ObsRagHitEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hitTraceId;
    private String httpTraceId;
    private Long tenantId;
    private Long userId;
    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long kbId;
    private Long documentId;
    private Long chunkId;
    private Integer chunkSeq;
    private RagRetrievalMode retrievalMode;
    private RagRetrievalHitSource hitSource;
    private BigDecimal vectorSimilarity;
    private BigDecimal keywordScore;
    private String queryText;
    private Integer rankInBatch;
    private LocalDateTime createdAt;

    /** 管理端展示字段，非表列。 */
    @TableField(exist = false)
    private String conversationTitle;

    @TableField(exist = false)
    private String userQuestionPreview;

    @TableField(exist = false)
    private String kbName;

    @TableField(exist = false)
    private String documentTitle;

    @TableField(exist = false)
    private String chunkLabel;
}
