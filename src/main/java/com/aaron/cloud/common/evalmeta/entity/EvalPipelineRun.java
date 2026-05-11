package com.aaron.cloud.common.evalmeta.entity;

import com.aaron.cloud.common.api.enums.EvalRunStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eval_pipeline_run")
public class EvalPipelineRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String pipelineCode;
    private EvalRunStatus status;
    private String inputRefJson;
    private String outputRefJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
