package com.aaron.cloud.common.rag.entity;

import com.aaron.cloud.common.api.enums.rag.RagDocumentDisplayStatus;
import com.aaron.cloud.common.api.enums.rag.RagDocumentSourceType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rag_document")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 0 有效，1 逻辑删除。 */
    private Integer deleted;

    private RagDocumentSourceType sourceType;

    private String title;
    private String sourceUri;
    private String originalFilename;
    private String mdContent;

    private Long contentLength;

    private Long categoryId;

    private RagDocumentDisplayStatus displayStatus;

    /** 适用范围（运营填写，如年级/科目）。 */
    private String applicableScope;

    private Long uploadedByUserId;

    /** 对话 RAG 召回写入 meta 时按分片命中累计到文档的计数。 */
    private Long hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
