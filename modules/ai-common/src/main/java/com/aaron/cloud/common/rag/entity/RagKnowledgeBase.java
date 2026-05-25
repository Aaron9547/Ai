package com.aaron.cloud.common.rag.entity;

import com.aaron.cloud.common.api.enums.rag.RagChunkStrategy;
import com.aaron.cloud.common.api.enums.gateway.ToggleState;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rag_knowledge_base")
public class RagKnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String name;

    /** 默认分片策略（文档入库沿用，可在任务 payload 覆盖）。 */
    private RagChunkStrategy defaultChunkStrategy;

    private Integer chunkFixedChars;
    private Integer chunkSlideOverlap;

    /** 对话编排绑定的租户可配语言模型 {@code llm_model.id}（{@code LANGUAGE}），可空。 */
    private Long assignedLlmModelId;

    /**
     * Milvus 入库与检索使用的嵌入端点 {@code llm_model.id}（须 {@code VECTOR}、启用且含 API Key）；与
     * {@code ai.providers.milvus.vector-dimension} 产出维数须一致。
     */
    private Long assignedEmbeddingModelId;

    /** 是否在对话编排中纳入本知识库检索；与分片级 {@code rag_chunk.retrieval_enabled} 独立。 */
    private ToggleState chatRetrievalEnabled;

    /**
     * 对话侧 Milvus COSINE 召回分数下限（仅本库生效；多库对话检索时对每个知识库分别使用该值）。
     * {@code 0} 表示该知识库不做向量分数过滤（仍可有 ES 词法命中等）；列默认 {@code 0.65}。
     */
    private Double chatVectorMinCosineScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
