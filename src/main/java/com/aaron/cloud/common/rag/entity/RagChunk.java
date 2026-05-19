package com.aaron.cloud.common.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.aaron.cloud.common.api.enums.RagChunkRetrievalEnabled;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rag_chunk")
public class RagChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 0 有效，1 逻辑删除。 */
    private Integer deleted;

    private String content;
    private String embeddingRef;

    /** 是否参与检索召回；禁用时不写入/可删除向量侧条目。 */
    private RagChunkRetrievalEnabled retrievalEnabled;

    /** 子母分片时指向母块；母块与扁平分片为 null。 */
    private Long parentChunkId;

    /** 对话 RAG 召回写入助手 meta 时对该分片的命中累计。 */
    private Long hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
