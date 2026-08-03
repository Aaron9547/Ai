package com.aaron.cloud.common.scheduled.entity;

import com.aaron.cloud.common.api.enums.scheduled.ScheduledRunStatus;
import com.aaron.cloud.common.api.enums.scheduled.ScheduledRunTrigger;
import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ten_scheduled_run")
public class TenantScheduledRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** {@code ten_scheduled_task.id} */
    private Long registrationId;

    private TenantScheduledExecutorCode executorCode;
    private ScheduledRunStatus status;
    private ScheduledRunTrigger triggerType;
    private String progressJson;
    /** 子任务 job_task.id 列表 JSON 数组 */
    private String childJobTaskIdsJson;
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
