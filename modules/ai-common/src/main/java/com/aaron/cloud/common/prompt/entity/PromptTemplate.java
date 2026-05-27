package com.aaron.cloud.common.prompt.entity;

import com.aaron.cloud.common.api.enums.prompt.PromptTemplateDomain;
import com.aaron.cloud.common.api.enums.prompt.PromptTemplateKind;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String promptCode;
    private PromptTemplateKind promptKind;
    private PromptTemplateDomain domain;
    private String locale;
    private String content;
    private String variablesSchemaJson;
    private Integer version;
    private Boolean enabled;
    private String remark;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
