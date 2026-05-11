package com.aaron.cloud.common.modelcfg.entity;

import com.aaron.cloud.common.api.enums.LlmAnonymousAccess;
import com.aaron.cloud.common.api.enums.LlmModelKind;
import com.aaron.cloud.common.api.enums.LlmModelStatus;
import com.aaron.cloud.common.api.enums.LlmThinkingCapability;
import com.aaron.cloud.common.api.enums.LlmVectorBackend;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("llm_model")
public class SysLlmModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String alias;
    private String displayName;
    private String openaiBaseUrl;
    private String openaiModelId;

    /** 模型类型；对话流仅 {@link LlmModelKind#LANGUAGE}。 */
    private LlmModelKind modelKind;

    /** 仅 {@link LlmModelKind#VECTOR} 语义有效；嵌入 URL 路径策略。 */
    private LlmVectorBackend vectorBackend;

    /**
     * 是否本地部署（默认否）：为 true 且 {@link LlmModelKind#VECTOR} 时，嵌入经 Feign 调用
     * {@code POST .../{tenantCode}/privateModel/embedding}（{@code tenantCode}={@code sys_tenant.code}）。根地址为
     * {@code ai.rag.local-embed-feign.base-url}（非空则直连），或 Eureka 上 {@code ai.rag.local-embed-feign.service-id}
     *（如 {@code ly-ai-rag-svc}，须 {@code ai.discovery.enabled=true} / {@code AI_DISCOVERY_ENABLED=true}）。非向量类型可存 0，不参与编排。
     */
    private Boolean localDeploy;

    /** AES-GCM 密文（Base64），可为空表示未配置密钥 */
    private String apiKeyCipher;

    private LlmAnonymousAccess allowAnonymous;
    private Integer maxAttachments;
    private LlmThinkingCapability supportsThinking;
    private LlmModelStatus status;
    private Integer sortOrder;

    /** 共用 token 上限；{@code null} 表示不限制（豆包/OpenAI 等流式 usage 累加至此） */
    private Long tokenQuotaTotal;

    /** 已消耗 token（租户内该模型配置共用） */
    private Long tokensUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
