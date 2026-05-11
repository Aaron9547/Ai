package com.aaron.cloud.common.jobmeta.entity;

import com.aaron.cloud.common.api.enums.JobTaskStatus;
import com.aaron.cloud.common.api.enums.JobTaskType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_task")
public class JobTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String deviceId;
    private JobTaskType taskType;
    private JobTaskStatus status;
    private String payloadJson;
    /** RAG 入队时与 payload 顶层 kbId 一致；供管理端按库分页，避免依赖库端 JSON 函数。 */
    private Long ragKbId;
    private String resultJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
