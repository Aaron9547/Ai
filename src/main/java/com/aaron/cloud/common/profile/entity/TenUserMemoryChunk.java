package com.aaron.cloud.common.profile.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ten_user_memory_chunk")
public class TenUserMemoryChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String subjectKey;
    private Long conversationId;

    /** {@link com.aaron.cloud.common.profile.MemoryChunkRoles} */
    private String chunkRole;

    private String contentSnippet;

    /** 向量索引标记（如 milvus），可空。 */
    private String vectorRef;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
