package com.aaron.cloud.common.chat.entity;

import com.aaron.cloud.common.api.enums.chat.ChatStarterDailyBatchStatus;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_starter_daily_batch")
public class ChatStarterDailyBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private LocalDate topicDate;
    private ChatStarterDailyBatchStatus status;
    private String questionsJson;
    private String errorMessage;
    private LocalDateTime fetchedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
