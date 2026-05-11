package com.aaron.cloud.common.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("lnk_rag_kb_document")
public class LnkRagKbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;
    private Long documentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
