package com.aaron.cloud.common.guardrail.entity;

import com.aaron.cloud.common.api.enums.guardrail.GuardrailSensitivePoolType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("guardrail_sensitive_term")
public class GuardrailSensitiveTerm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private GuardrailSensitivePoolType poolType;

    /**
     * {@link GuardrailSensitivePoolType#PLATFORM} 时固定为 0；{@link GuardrailSensitivePoolType#TENANT} 时为租户 id。
     */
    private Long tenantId;

    private String word;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
