package com.aaron.cloud.common.scheduled.entity;

import com.aaron.cloud.common.api.enums.scheduled.TenantScheduledExecutorCode;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 租户定时任务调度注册（仅 cron + 执行器 + 执行时间；业务参数在执行器内维护）。 */
@Data
@TableName("ten_scheduled_task")
public class TenantScheduledTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 与 {@link #executorCode} 同步，兼容历史 task_type 列。 */
    private String taskType;

    private String name;
    private Integer enabled;
    private TenantScheduledExecutorCode executorCode;
    private String cronExpression;

    @TableField("last_run_at")
    private LocalDateTime lastExecAt;

    private LocalDateTime nextExecAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
